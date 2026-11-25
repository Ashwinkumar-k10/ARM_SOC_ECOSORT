"""
arm_submission/models/export_llama_executorch.py
EcoSort-Edge 2.0: Arm ExecuTorch Llama 3.2 1B-Instruct Export & XNNPACK Lowering.

Produces a unified .pte binary + tokenizer.model for:
  1. Raspberry Pi 5 (Quad-Core Arm Cortex-A76, NEON/SDOT)
  2. Arm-based Android Smartphones (ExecuTorch Android LlmModule)

Quantization & Lowering Profile:
  - Architecture   : llama3_2 (Meta Llama-3.2-1B-Instruct)
  - Weight Quant   : 4-bit groupwise (qmode=8da4w, group_size=128)
  - Activation     : 8-bit dynamic per-token activations
  - Embeddings     : 4-bit groupwise (4,32)
  - KV Cache       : Enabled with SDPA (--use_kv_cache --use_sdpa_with_kv_cache)
  - Delegate       : Arm XNNPACK (-X)
"""

import argparse
import json
import os
import shutil
import subprocess
import sys
from pathlib import Path
from typing import Tuple

MODELS_DIR = Path(__file__).resolve().parent


def resolve_checkpoint_files(repo_id_or_dir: str) -> Tuple[Path, Path, Path]:
    """Locate or download original Llama 3.2 checkpoint, params.json, and tokenizer.model."""
    local_repo = Path(repo_id_or_dir)
    if not local_repo.exists():
        from huggingface_hub import snapshot_download

        print(f"[*] Resolving {repo_id_or_dir} (original/*) from HuggingFace...")
        token = os.environ.get("HF_TOKEN")
        local_repo = Path(
            snapshot_download(
                repo_id=repo_id_or_dir,
                token=token,
                allow_patterns=["original/*"],
            )
        )

    candidates = [local_repo / "original", local_repo]
    for base in candidates:
        ckpt = base / "consolidated.00.pth"
        params = base / "params.json"
        tok = base / "tokenizer.model"
        if ckpt.exists() and params.exists() and tok.exists():
            return ckpt, params, tok

    raise FileNotFoundError(
        f"Expected consolidated.00.pth, params.json, and tokenizer.model inside {local_repo}."
    )


def verify_pte_runtime(pte_path: Path) -> None:
    """Verify the exported .pte loads cleanly in ExecuTorch Runtime."""
    from executorch.runtime import Runtime

    rt = Runtime.get()
    program = rt.load_program(str(pte_path))
    methods = program.method_names
    print(f"[✓] ExecuTorch Runtime Verified: {pte_path.name} | Methods: {list(methods)}")


def export_llama_pte(
    repo_id: str,
    output_name: str,
    max_seq_len: int,
    max_context_len: int,
    qmode: str,
    group_size: int,
) -> Path:
    ckpt_path, params_path, tokenizer_src = resolve_checkpoint_files(repo_id)

    out_pte = MODELS_DIR / output_name
    out_tok = MODELS_DIR / "tokenizer.model"
    shutil.copy2(tokenizer_src, out_tok)
    print(f"[✓] Tokenizer staged: {out_tok}")

    metadata = json.dumps({"get_bos_id": 128000, "get_eos_ids": [128009, 128001]})

    cmd = [
        sys.executable,
        "-m",
        "executorch.examples.models.llama.export_llama",
        "--model", "llama3_2",
        "-c", str(ckpt_path),
        "-p", str(params_path),
        "-t", str(out_tok),
        "-kv",
        "--use_sdpa_with_kv_cache",
        "-d", "fp32",
    ]
    if qmode in ("8da4w", "8da4w-gptq"):
        cmd.extend(["-qmode", qmode, "-G", str(group_size), "-E", "4,32"])
    elif qmode == "int8":
        # True compact Arm XNNPACK INT8 via PT2E dynamic per-channel quantization
        cmd.extend(["--pt2e_quantize", "xnnpack_dynamic", "-E", "8,1024"])

    cmd.extend([
        "--metadata", metadata,
        "--max_context_length", str(max(max_context_len, max_seq_len)),
        "--max_seq_length", str(max_seq_len),
        "-X",
        "-n", str(out_pte),
    ])

    print("=" * 80)
    print(f" [ARM EXECUTORCH] LLAMA 3.2 1B-INSTRUCT ({qmode.upper()} + XNNPACK) EXPORT")
    print("=" * 80)
    print("[*] Command:", " ".join(cmd))
    subprocess.run(cmd, check=True)

    if not out_pte.exists():
        raise FileNotFoundError(f"Export finished but output binary missing: {out_pte}")

    verify_pte_runtime(out_pte)
    size_mb = out_pte.stat().st_size / (1024 * 1024)
    print(f"[✓] Llama ExecuTorch Binary Ready: {out_pte} ({size_mb:.1f} MB)")
    return out_pte


def main():
    parser = argparse.ArgumentParser(description="Export Llama 3.2 1B-Instruct INT8 to Arm ExecuTorch .pte")
    parser.add_argument("--repo-id", type=str, default="meta-llama/Llama-3.2-1B-Instruct")
    parser.add_argument("--output-name", type=str, default="llama3_2_1b_int8.pte")
    parser.add_argument("--qmode", type=str, default="int8", choices=["int8"])
    parser.add_argument("--max-seq-len", type=int, default=512)
    parser.add_argument("--max-context-len", type=int, default=1024)
    parser.add_argument("--group-size", type=int, default=32)
    args = parser.parse_args()

    export_llama_pte(
        repo_id=args.repo_id,
        output_name=args.output_name,
        max_seq_len=args.max_seq_len,
        max_context_len=args.max_context_len,
        qmode=args.qmode,
        group_size=args.group_size,
    )


if __name__ == "__main__":
    main()
