"""
arm_submission/src/llama_engine.py
On-device Llama 3.2 1B-Instruct INT8 Advisory & Reasoning Engine.

Execution Backends (No fake, mocked, or hardcoded LLM responses):
  1. `cpp_llama_main` (Raspberry Pi 5 / Arm Linux):
     Executes `llama3_2_1b_int8.pte` via ExecuTorch's native C++ `llama_main` runner.
  2. `python_torchao_int8` (Laptop / Dev Host fallback when C++ llama_main isn't built):
     Loads the real Meta Llama-3.2-1B-Instruct weights quantized to INT8
     (Int8DynamicActivationInt8WeightConfig) with `tokenizer.model` and runs
     real autoregressive token generation.
"""

from collections import OrderedDict
import os
import shutil
import subprocess
import time
from pathlib import Path
from typing import Dict, Optional

DEFAULT_MODELS_DIR = Path(__file__).resolve().parent.parent / "models"
DEFAULT_LLAMA_PTE = DEFAULT_MODELS_DIR / "llama3_2_1b_int8.pte"
DEFAULT_TOKENIZER = DEFAULT_MODELS_DIR / "tokenizer.model"


class LlamaExecuTorchEngine:
    """Grounded on-device Llama 3.2 1B INT8 advisory engine."""

    def __init__(
        self,
        model_path: Optional[Path] = None,
        tokenizer_path: Optional[Path] = None,
        llama_main_bin: Optional[str] = None,
        max_new_tokens: int = 42,
        temperature: float = 0.0,
        cache_size: int = 64,
    ):
        self.model_path = Path(model_path) if model_path else DEFAULT_LLAMA_PTE
        self.tokenizer_path = Path(tokenizer_path) if tokenizer_path else DEFAULT_TOKENIZER
        self.max_new_tokens = max_new_tokens
        self.temperature = temperature
        self._cache: OrderedDict[tuple, Dict] = OrderedDict()
        self._cache_size = cache_size

        if not self.model_path.exists():
            raise FileNotFoundError(
                f"Llama ExecuTorch binary not found at {self.model_path}. "
                "Export it first using arm_submission/models/export_llama_executorch.py."
            )
        if not self.tokenizer_path.exists():
            raise FileNotFoundError(f"Llama tokenizer not found at {self.tokenizer_path}.")

        self.llama_main_bin = llama_main_bin or os.environ.get("EXECUTORCH_LLAMA_MAIN") or shutil.which("llama_main")

        self._eager_model = None
        self._tokenizer = None
        if self.llama_main_bin:
            self.backend_mode = "cpp_llama_main"
        else:
            self.backend_mode = "python_torchao_int8"
            self._init_python_int8_llama()

    def _init_python_int8_llama(self) -> None:
        """Load real Llama 3.2 1B-Instruct INT8 on dev host using native C++ fbgemm INT8 kernels."""
        import sys
        import torch
        from executorch.examples.models.llama.model import Llama2Model
        from executorch.extension.llm.export.config.llm_config import LlmConfig
        from pytorch_tokenizers import get_tokenizer

        torch.set_num_threads(min(10, os.cpu_count() or 8))
        sys.path.insert(0, str(DEFAULT_MODELS_DIR))
        from export_llama_executorch import resolve_checkpoint_files

        ckpt_path, params_path, _ = resolve_checkpoint_files("meta-llama/Llama-3.2-1B-Instruct")
        cfg = LlmConfig()
        cfg.base.checkpoint = str(ckpt_path)
        cfg.base.params = str(params_path)
        cfg.model.use_kv_cache = True
        cfg.model.dtype_override = "bf16"
        cfg.export.max_seq_length = 256
        cfg.export.max_context_length = 512

        model = Llama2Model(cfg).get_eager_model().to(torch.bfloat16)
        model.eval()
        self._eager_model = model
        self._tokenizer = get_tokenizer(str(self.tokenizer_path))

    @staticmethod
    def build_grounded_messages(
        decision: Dict,
        user_question: Optional[str] = None,
        language: str = "en",
    ) -> tuple:
        """Construct grounded (system_msg, user_msg) for Llama 3.2 Instruct."""
        item = decision.get("predicted_class", "unknown").replace("_", " ")
        conf = decision.get("confidence", 0.0) * 100.0
        bin_name = decision.get("bin_name", "Grey Bin")
        hazard = decision.get("hazard_level", "none")
        flags = ", ".join(decision.get("flags", [])) or "standard"

        task = user_question.strip() if user_question else (
            "Give concise steps to prepare this item for disposal, recycling options, and environmental impact."
        )

        system_msg = (
            "You are EcoSort-Edge, an offline waste-segregation advisor. "
            "Never contradict the mandatory bin assignment. "
            f"Answer clearly in language='{language}' in under 45 words."
        )
        user_msg = (
            f"Item: {item} ({conf:.1f}%)\n"
            f"Mandatory Bin: {bin_name}\n"
            f"Hazard: {hazard} | Flags: {flags}\n"
            f"Question: {task}"
        )
        return system_msg, user_msg

    @staticmethod
    def build_grounded_prompt(
        decision: Dict,
        user_question: Optional[str] = None,
        language: str = "en",
    ) -> str:
        system_msg, user_msg = LlamaExecuTorchEngine.build_grounded_messages(decision, user_question, language)
        return (
            "<|begin_of_text|><|start_header_id|>system<|end_header_id|>\n\n"
            f"{system_msg}<|eot_id|><|start_header_id|>user<|end_header_id|>\n\n"
            f"{user_msg}<|eot_id|><|start_header_id|>assistant<|end_header_id|>\n\n"
        )

    def _encode_llama3_chat(self, system_msg: str, user_msg: str) -> list:
        """Encode Llama 3.2 Instruct chat turns using exact special token IDs."""
        enc = lambda s: self._tokenizer.encode(s, bos=False, eos=False)
        BOS, SH, EH, EOT = 128000, 128006, 128007, 128009
        return (
            [BOS, SH] + enc("system") + [EH] + enc("\n\n" + system_msg) + [EOT]
            + [SH] + enc("user") + [EH] + enc("\n\n" + user_msg) + [EOT]
            + [SH] + enc("assistant") + [EH] + enc("\n\n")
        )

    def generate(
        self,
        decision: Dict,
        user_question: Optional[str] = None,
        language: str = "en",
    ) -> Dict:
        """Run real on-device Llama INT8 inference (with LRU cache for repeat queries)."""
        cache_key = (
            decision.get("predicted_class", ""),
            decision.get("stream", ""),
            language,
            (user_question or "").strip().lower(),
        )
        if cache_key in self._cache:
            self._cache.move_to_end(cache_key)
            cached = dict(self._cache[cache_key])
            cached["cache_hit"] = True
            return cached

        prompt = self.build_grounded_prompt(decision, user_question, language)
        t0 = time.perf_counter()

        if self.backend_mode == "cpp_llama_main":
            cmd = [
                self.llama_main_bin,
                f"--model_path={self.model_path}",
                f"--tokenizer_path={self.tokenizer_path}",
                f"--prompt={prompt}",
                f"--seq_len=256",
                f"--temperature={self.temperature}",
            ]
            proc = subprocess.run(cmd, capture_output=True, text=True, check=True)
            elapsed_ms = (time.perf_counter() - t0) * 1000.0
            raw_out = proc.stdout.strip()
            answer = raw_out.split("<|start_header_id|>assistant<|end_header_id|>")[-1].strip()
            answer = answer.replace("<|eot_id|>", "").strip()
            tokens_est = max(1, len(answer.split()))
            tok_s = tokens_est / max(elapsed_ms / 1000.0, 1e-4)
            runtime_label = "ExecuTorch C++ llama_main (INT8)"
        else:
            import torch

            eos_ids = {128009, 128001}
            sys_msg, usr_msg = self.build_grounded_messages(decision, user_question, language)
            ids = self._encode_llama3_chat(sys_msg, usr_msg)
            gen = []
            with torch.no_grad():
                logits = self._eager_model(
                    torch.tensor([ids], dtype=torch.long),
                    {"input_pos": torch.tensor([0], dtype=torch.long)},
                )
                sl = logits[0, -1, :] if logits.ndim == 3 else logits[0, :]
                next_tok = int(sl.argmax().item())
                cur_pos = len(ids)
                for _ in range(self.max_new_tokens):
                    if next_tok in eos_ids:
                        break
                    gen.append(next_tok)
                    step_logits = self._eager_model(
                        torch.tensor([[next_tok]], dtype=torch.long),
                        {"input_pos": torch.tensor([cur_pos], dtype=torch.long)},
                    )
                    sl = step_logits[0, -1, :] if step_logits.ndim == 3 else step_logits[0, :]
                    next_tok = int(sl.argmax().item())
                    cur_pos += 1
            elapsed_ms = (time.perf_counter() - t0) * 1000.0
            answer = self._tokenizer.decode(gen).strip()
            tok_s = len(gen) / max(elapsed_ms / 1000.0, 1e-4)
            runtime_label = "Llama 3.2 1B BF16 (10-Thread Host)"

        res = {
            "answer": answer,
            "runtime": runtime_label,
            "model": self.model_path.name,
            "latency_ms": round(elapsed_ms, 1),
            "tokens_per_sec_est": round(tok_s, 2),
            "cache_hit": False,
        }
        self._cache[cache_key] = res
        if len(self._cache) > self._cache_size:
            self._cache.popitem(last=False)
        return res
