"""
arm_submission/benchmarks/ablate_llama_quantization.py
Empirical Laptop Ablation for Meta Llama 3.2 1B-Instruct Quantization Profiles:
  1. BF16 Baseline (Unquantized reference)
  2. 8da4w (G=128, E=4,32) -> llama3_2_1b_q4.pte
  3. 8da4w (G=32,  E=4,32) -> llama3_2_1b_q4_g32.pte
  4. int8  (8da8w, E=8,1024) -> llama3_2_1b_int8.pte

Measures on real EcoSort-Edge waste-sorting prompts:
  - Exported .pte binary size (MB)
  - Logit Cosine Similarity vs. BF16
  - Top-1 Token Agreement vs. BF16
  - Real CPU generation speed (tok/s) & actual generated responses
"""

import json
import os
import sys
import time
from pathlib import Path
from typing import Dict, List

import numpy as np
import torch
import torch.nn.functional as F

SUBMISSION_ROOT = Path(__file__).resolve().parent.parent
MODELS_DIR = SUBMISSION_ROOT / "models"
sys.path.insert(0, str(MODELS_DIR))

from export_llama_executorch import resolve_checkpoint_files

PROMPTS = [
    {
        "id": "plastic_bottle",
        "text": (
            "<|begin_of_text|><|start_header_id|>system<|end_header_id|>\n\n"
            "You are EcoSort-Edge. Follow the mandatory bin assignment and reply in under 45 words.<|eot_id|>"
            "<|start_header_id|>user<|end_header_id|>\n\n"
            "Detected item: plastic_bottle (98.5% confidence). Mandatory Bin: Blue Bin (Dry Recyclable). "
            "How should I prepare it and why does recycling it matter?<|eot_id|>"
            "<|start_header_id|>assistant<|end_header_id|>\n\n"
        ),
    },
    {
        "id": "battery_hazard",
        "text": (
            "<|begin_of_text|><|start_header_id|>system<|end_header_id|>\n\n"
            "You are EcoSort-Edge. Follow the mandatory bin assignment and reply in under 45 words.<|eot_id|>"
            "<|start_header_id|>user<|end_header_id|>\n\n"
            "Detected item: battery (76.4% confidence). Mandatory Bin: Red Bin (Special-Care / Domestic Hazardous). "
            "Why is this dangerous in normal trash and how do I dispose of it safely?<|eot_id|>"
            "<|start_header_id|>assistant<|end_header_id|>\n\n"
        ),
    },
    {
        "id": "greasy_cardboard",
        "text": (
            "<|begin_of_text|><|start_header_id|>system<|end_header_id|>\n\n"
            "You are EcoSort-Edge. Follow the mandatory bin assignment and reply in under 45 words.<|eot_id|>"
            "<|start_header_id|>user<|end_header_id|>\n\n"
            "Detected item: cardboard (40.0% confidence). Mandatory Bin: Blue Bin (Dry Recyclable). "
            "Can I recycle a cardboard pizza box if half of it has food grease?<|eot_id|>"
            "<|start_header_id|>assistant<|end_header_id|>\n\n"
        ),
    },
]


def load_llama_variant(ckpt_path: Path, params_path: Path, qmode: str, group_size: int):
    from executorch.examples.models.llama.model import Llama2Model
    from executorch.extension.llm.export.config.llm_config import LlmConfig
    from torchao.quantization import (
        Int8DynActInt4WeightQuantizer,
        Int8DynamicActivationInt8WeightConfig,
        quantize_,
    )

    cfg = LlmConfig()
    cfg.base.checkpoint = str(ckpt_path)
    cfg.base.params = str(params_path)
    cfg.model.use_kv_cache = True
    cfg.model.dtype_override = "fp32"
    cfg.export.max_seq_length = 512
    cfg.export.max_context_length = 1024

    wrapper = Llama2Model(cfg)
    model = wrapper.get_eager_model().to(torch.float32)
    model.eval()

    if qmode == "8da4w":
        model = Int8DynActInt4WeightQuantizer(groupsize=group_size).quantize(model)
    elif qmode == "int8":
        quantize_(model, Int8DynamicActivationInt8WeightConfig())
    return model


def run_eval(model, tok, max_new_tokens: int = 24):
    eos_ids = {128009, 128001}
    outputs = {}
    with torch.no_grad():
        for idx, p in enumerate(PROMPTS):
            ids = tok.encode(p["text"], bos=False, eos=False)
            x = torch.tensor([ids], dtype=torch.long)
            pos = torch.tensor([0], dtype=torch.long)
            t0 = time.perf_counter()
            logits = model(x, {"input_pos": pos})
            first_logits = (logits[0, -1, :] if logits.ndim == 3 else logits[0, :]).float().cpu()
            ttft_ms = (time.perf_counter() - t0) * 1000.0

            next_tok = int(first_logits.argmax().item())
            gen = [next_tok]
            cur_pos = len(ids)

            tok_s = 0.0
            if idx == 0:
                t_dec = time.perf_counter()
                for _ in range(max_new_tokens - 1):
                    if next_tok in eos_ids:
                        break
                    step_logits = model(
                        torch.tensor([[next_tok]], dtype=torch.long),
                        {"input_pos": torch.tensor([cur_pos], dtype=torch.long)},
                    )
                    sl = step_logits[0, -1, :] if step_logits.ndim == 3 else step_logits[0, :]
                    next_tok = int(sl.argmax().item())
                    gen.append(next_tok)
                    cur_pos += 1
                dec_s = time.perf_counter() - t_dec
                tok_s = len(gen) / max(dec_s, 1e-4)

            outputs[p["id"]] = {
                "first_logits": first_logits,
                "top1_token": int(first_logits.argmax().item()),
                "ttft_ms": round(ttft_ms, 1),
                "tok_per_sec": round(tok_s, 2),
                "response": tok.decode([t for t in gen if t not in eos_ids]).strip() if idx == 0 else "",
            }
    return outputs


def main():
    from pytorch_tokenizers import get_tokenizer

    ckpt_path, params_path, tok_path = resolve_checkpoint_files("meta-llama/Llama-3.2-1B-Instruct")
    tok = get_tokenizer(str(tok_path))

    variants = [
        {"name": "BF16 Baseline", "qmode": "bf16", "group_size": 0, "pte": None},
        {"name": "8da4w (G=128)", "qmode": "8da4w", "group_size": 128, "pte": MODELS_DIR / "llama3_2_1b_q4.pte"},
        {"name": "8da4w (G=32)", "qmode": "8da4w", "group_size": 32, "pte": MODELS_DIR / "llama3_2_1b_q4_g32.pte"},
        {"name": "INT8 (8da8w)", "qmode": "int8", "group_size": 0, "pte": MODELS_DIR / "llama3_2_1b_int8.pte"},
    ]

    ref_outputs = None
    summary = []

    for v in variants:
        print(f"\n[*] Benchmarking {v['name']}...")
        model = load_llama_variant(ckpt_path, params_path, v["qmode"], v["group_size"])
        out = run_eval(model, tok)
        del model

        if ref_outputs is None:
            ref_outputs = out

        cos_list, agree_list, ttft_list, tps_list = [], [], [], []
        samples = {}
        for pid, data in out.items():
            ref = ref_outputs[pid]
            cos = float(F.cosine_similarity(data["first_logits"].unsqueeze(0), ref["first_logits"].unsqueeze(0)).item())
            cos_list.append(cos)
            agree_list.append(1.0 if data["top1_token"] == ref["top1_token"] else 0.0)
            ttft_list.append(data["ttft_ms"])
            tps_list.append(data["tok_per_sec"])
            samples[pid] = data["response"]

        pte_mb = round(v["pte"].stat().st_size / (1024 * 1024), 1) if (v["pte"] and v["pte"].exists()) else None
        row = {
            "variant": v["name"],
            "pte_size_mb": pte_mb,
            "cosine_vs_bf16": round(float(np.mean(cos_list)), 4),
            "top1_agreement_pct": round(float(np.mean(agree_list)) * 100.0, 1),
            "avg_ttft_ms": round(float(np.mean(ttft_list)), 1),
            "avg_tok_per_sec": round(float(np.mean(tps_list)), 2),
            "responses": samples,
        }
        summary.append(row)
        print(f"    PTE: {pte_mb} MB | Cosine: {row['cosine_vs_bf16']} | Top1 Agree: {row['top1_agreement_pct']}% | TTFT: {row['avg_ttft_ms']} ms | Speed: {row['avg_tok_per_sec']} tok/s")

    out_json = SUBMISSION_ROOT / "benchmarks" / "llama_quantization_ablation.json"
    out_json.write_text(json.dumps(summary, indent=2), encoding="utf-8")
    print(f"\n[✓] Ablation report saved: {out_json}")


if __name__ == "__main__":
    main()
