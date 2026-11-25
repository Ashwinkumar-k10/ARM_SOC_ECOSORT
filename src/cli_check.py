"""
arm_submission/src/cli_check.py
Pure Terminal CLI Checker for EcoSort-Edge 2.0 (No Web UI).
Runs ExecuTorch INT8 Vision -> SWM 2026 PolicyGuard -> Llama 3.2 1B INT8 directly in terminal.

Usage:
  python src/cli_check.py --image benchmark_30/item_01_battery_01.jpg
  python src/cli_check.py --image benchmark_30/item_25_plastic_bottle_02.jpg --question "How to prep this?"
  python src/cli_check.py --batch30
  python src/cli_check.py --image benchmark_30/item_01_battery_01.jpg --no-llama
"""

import argparse
import json
import sys
from pathlib import Path
import cv2
import numpy as np

SRC_DIR = Path(__file__).resolve().parent
SUBMISSION_ROOT = SRC_DIR.parent
sys.path.insert(0, str(SRC_DIR))

from policy_guard import PolicyGuard
from vision_engine import VisionEngine
from llama_engine import LlamaExecuTorchEngine


def run_single(vision: VisionEngine, policy: PolicyGuard, llama, img_path: Path, question: str = ""):
    if not img_path.exists():
        raise FileNotFoundError(f"Image not found: {img_path}")
    frame = cv2.imread(str(img_path))
    if frame is None:
        raise ValueError(f"Could not decode image: {img_path}")

    vis = vision.classify_frame(frame)
    dec = policy.evaluate(vis["predicted_class"], vis["confidence"], vis["entropy"])

    print("=" * 78)
    print(f" IMAGE          : {img_path.name}")
    print(f" Predicted      : {dec['predicted_class']} ({dec['confidence']*100:.1f}%) | Entropy H = {dec['entropy']}")
    print(f" Vision Latency : {vis['inference_ms']} ms (BBox: {vis['bbox']})")
    print(f" SWM 2026 Bin   : {dec['bin_name']} [{dec['status']}]")
    print(f" Hazard / Flags : {dec['hazard_level']} | {', '.join(dec['flags'])}")
    print("=" * 78)

    if llama is not None:
        print("[*] Running Llama 3.2 1B INT8...")
        adv = llama.generate(decision=dec, user_question=question or None)
        print(f" Engine  : {adv['runtime']} ({adv['latency_ms']} ms | {adv['tokens_per_sec_est']} tok/s)")
        print(f" Advice  : {adv['answer']}")
        print("=" * 78)


def run_batch30(vision: VisionEngine, policy: PolicyGuard):
    b30_dir = SUBMISSION_ROOT / "benchmark_30"
    gt_items = json.loads((b30_dir / "ground_truth.json").read_text(encoding="utf-8"))

    lats = []
    cls_ok = stream_ok = haz_tot = haz_ok = 0
    print("=" * 78)
    print(" ECOSORT-EDGE 2.0 — TERMINAL BENCHMARK 30 CHECK")
    print("=" * 78)
    for item in gt_items:
        frame = cv2.imread(str(b30_dir / item["filename"]))
        vis = vision.classify_frame(frame)
        dec = policy.evaluate(vis["predicted_class"], vis["confidence"], vis["entropy"])
        lats.append(vis["inference_ms"])

        c_m = vis["predicted_class"] == item["true_class"]
        s_m = dec["stream"] == item["statutory_stream"]
        cls_ok += int(c_m)
        stream_ok += int(s_m)
        if item["statutory_stream"] == "special_care":
            haz_tot += 1
            haz_ok += int(dec["stream"] == "special_care")

        tag = "OK" if s_m else "MISS"
        print(
            f"[{tag:4s}] {item['sample_id']} | True: {item['true_class']:21s} | "
            f"Pred: {vis['predicted_class']:21s} ({vis['confidence']*100:5.1f}%) | "
            f"Bin: {dec['stream']:12s} | {vis['inference_ms']:5.2f} ms"
        )

    n = len(gt_items)
    print("=" * 78)
    print(f" Class Accuracy        : {(cls_ok/n)*100:.2f}% ({cls_ok}/{n})")
    print(f" SWM 2026 Compliance   : {(stream_ok/n)*100:.2f}% ({stream_ok}/{n})")
    print(f" Battery Hazard Recall : {(haz_ok/haz_tot)*100:.2f}% ({haz_ok}/{haz_tot})")
    print(f" Avg / P95 Latency     : {np.mean(lats):.2f} ms / {np.percentile(lats, 95):.2f} ms")
    print("=" * 78)


def main():
    parser = argparse.ArgumentParser(description="EcoSort-Edge 2.0 Terminal Checker")
    parser.add_argument("--image", type=Path, help="Path to an image to classify and advise on")
    parser.add_argument("--question", type=str, default="", help="Optional question for Llama 3.2 1B INT8")
    parser.add_argument("--batch30", action="store_true", help="Run all 30 benchmark_30 images in terminal")
    parser.add_argument("--no-llama", action="store_true", help="Skip loading Llama (fast vision+policy check)")
    args = parser.parse_args()

    vision = VisionEngine()
    policy = PolicyGuard()
    llama = None if (args.no_llama or args.batch30) else LlamaExecuTorchEngine()

    if args.batch30:
        run_batch30(vision, policy)
    elif args.image:
        run_single(vision, policy, llama, args.image, args.question)
    else:
        default_img = SUBMISSION_ROOT / "benchmark_30" / "item_01_battery_01.jpg"
        run_single(vision, policy, llama, default_img, args.question)


if __name__ == "__main__":
    main()
