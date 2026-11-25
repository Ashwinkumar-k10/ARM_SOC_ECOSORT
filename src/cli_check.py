"""
arm_submission/src/cli_check.py
Pure Terminal CLI Checker for EcoSort-Edge 2.0 (No Web UI).
Runs ExecuTorch INT8 Vision -> SWM 2026 PolicyGuard -> Llama 3.2 1B INT8 directly in terminal.

Usage:
  python src/cli_check.py --image benchmark_30/item_01_battery_01.jpg
  python src/cli_check.py --image benchmark_30/item_25_plastic_bottle_02.jpg --question "How to prep this?"
  python src/cli_check.py --batch10
  python src/cli_check.py --batch10 --no-llama
  python src/cli_check.py --batch30
  python src/cli_check.py --image benchmark_30/item_01_battery_01.jpg --no-llama
"""

import argparse
import json
import os
import platform
import subprocess
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


def read_pi5_telemetry():
    info = {
        "arch": platform.machine(),
        "platform": sys.platform,
        "cpu_cores": os.cpu_count(),
        "temp_c": None,
        "volts_v": None,
        "power_w_est": None,
        "arm_freq_mhz": None,
        "ram_used_mb": None,
        "ram_total_mb": None,
    }
    try:
        t_out = subprocess.check_output(["vcgencmd", "measure_temp"], text=True, timeout=1.0).strip()
        info["temp_c"] = float(t_out.split("=")[1].replace("'C", ""))
    except Exception:
        pass
    try:
        v_out = subprocess.check_output(["vcgencmd", "measure_volts", "core"], text=True, timeout=1.0).strip()
        volts = float(v_out.split("=")[1].replace("V", ""))
        info["volts_v"] = volts
        info["power_w_est"] = round(5.5 * volts, 2)
    except Exception:
        pass
    try:
        c_out = subprocess.check_output(["vcgencmd", "measure_clock", "arm"], text=True, timeout=1.0).strip()
        info["arm_freq_mhz"] = round(int(c_out.split("=")[1]) / 1e6, 1)
    except Exception:
        pass
    try:
        meminfo = Path("/proc/meminfo").read_text(encoding="utf-8")
        vals = {}
        for line in meminfo.splitlines():
            parts = line.split(":")
            if len(parts) == 2:
                vals[parts[0]] = int(parts[1].strip().split()[0])
        if "MemTotal" in vals and "MemAvailable" in vals:
            info["ram_total_mb"] = round(vals["MemTotal"] / 1024.0, 1)
            info["ram_used_mb"] = round((vals["MemTotal"] - vals["MemAvailable"]) / 1024.0, 1)
    except Exception:
        pass
    return info


def select_balanced_10(gt_items):
    picked = []
    seen_classes = set()
    for item in gt_items:
        cls = item["true_class"]
        if cls not in seen_classes:
            picked.append(item)
            seen_classes.add(cls)
        if len(picked) == 10:
            return picked
    for item in gt_items:
        if item not in picked:
            picked.append(item)
        if len(picked) == 10:
            break
    return picked


def run_batch(vision: VisionEngine, policy: PolicyGuard, llama=None, limit: int = 30):
    b30_dir = SUBMISSION_ROOT / "benchmark_30"
    gt_items = json.loads((b30_dir / "ground_truth.json").read_text(encoding="utf-8"))
    items = select_balanced_10(gt_items) if limit == 10 else gt_items[:limit]

    lats, confs, ents = [], [], []
    cls_ok = stream_ok = haz_tot = haz_ok = 0
    rows = []

    print("=" * 78)
    print(f" ECOSORT-EDGE 2.0 — TERMINAL BENCHMARK ({len(items)} IMAGES) + REPORT METRICS")
    print("=" * 78)
    for item in items:
        frame = cv2.imread(str(b30_dir / item["filename"]))
        vis = vision.classify_frame(frame)
        dec = policy.evaluate(vis["predicted_class"], vis["confidence"], vis["entropy"])
        lats.append(vis["inference_ms"])
        confs.append(vis["confidence"])
        ents.append(vis["entropy"])

        c_m = vis["predicted_class"] == item["true_class"]
        s_m = dec["stream"] == item["statutory_stream"]
        cls_ok += int(c_m)
        stream_ok += int(s_m)
        if item["statutory_stream"] == "special_care":
            haz_tot += 1
            haz_ok += int(dec["stream"] == "special_care")

        rows.append({
            "sample_id": item["sample_id"],
            "filename": item["filename"],
            "true_class": item["true_class"],
            "predicted_class": vis["predicted_class"],
            "true_stream": item["statutory_stream"],
            "predicted_stream": dec["stream"],
            "confidence": round(float(vis["confidence"]), 4),
            "entropy": round(float(vis["entropy"]), 4),
            "inference_ms": round(float(vis["inference_ms"]), 2),
            "stream_match": s_m,
            "class_match": c_m,
        })

        tag = "OK" if s_m else "MISS"
        print(
            f"[{tag:4s}] {item['sample_id']} | True: {item['true_class']:21s} | "
            f"Pred: {vis['predicted_class']:21s} ({vis['confidence']*100:5.1f}%) | "
            f"Bin: {dec['stream']:12s} | {vis['inference_ms']:5.2f} ms"
        )

    n = len(items)
    mean_ms = float(np.mean(lats))
    p50_ms = float(np.percentile(lats, 50))
    p95_ms = float(np.percentile(lats, 95))
    fps = round(1000.0 / mean_ms, 2) if mean_ms > 0 else 0.0
    telem = read_pi5_telemetry()

    vision_pte = SUBMISSION_ROOT / "models" / "waste_classifier_int8.pte"
    llama_pte = SUBMISSION_ROOT / "models" / "llama3_2_1b_int8.pte"
    vision_mb = round(vision_pte.stat().st_size / (1024 * 1024), 2) if vision_pte.exists() else None
    llama_mb = round(llama_pte.stat().st_size / (1024 * 1024), 2) if llama_pte.exists() else None

    llama_result = None
    if llama is not None:
        print("-" * 78)
        print("[*] Benchmarking Llama 3.2 1B INT8 on sample hazardous item...")
        sample_frame = cv2.imread(str(b30_dir / items[0]["filename"]))
        svis = vision.classify_frame(sample_frame)
        sdec = policy.evaluate(svis["predicted_class"], svis["confidence"], svis["entropy"])
        adv = llama.generate(decision=sdec, user_question=None)
        llama_result = {
            "runtime": adv["runtime"],
            "latency_ms": adv["latency_ms"],
            "tokens_per_sec_est": adv["tokens_per_sec_est"],
            "answer": adv["answer"],
        }

    report = {
        "benchmark_count": n,
        "vision_model_mb": vision_mb,
        "llama_model_mb": llama_mb,
        "class_accuracy_pct": round((cls_ok / n) * 100.0, 2),
        "stream_compliance_pct": round((stream_ok / n) * 100.0, 2),
        "hazard_recall_pct": round((haz_ok / haz_tot) * 100.0, 2) if haz_tot else None,
        "latency_mean_ms": round(mean_ms, 2),
        "latency_p50_ms": round(p50_ms, 2),
        "latency_p95_ms": round(p95_ms, 2),
        "latency_min_ms": round(float(np.min(lats)), 2),
        "latency_max_ms": round(float(np.max(lats)), 2),
        "throughput_fps": fps,
        "mean_confidence": round(float(np.mean(confs)), 4),
        "mean_entropy": round(float(np.mean(ents)), 4),
        "soc_telemetry": telem,
        "llama_benchmark": llama_result,
        "samples": rows,
    }

    out_path = SUBMISSION_ROOT / "benchmarks" / f"pi5_cli_{n}_report.json"
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(json.dumps(report, indent=2), encoding="utf-8")

    print("=" * 78)
    print(f" Architecture / CPU    : {telem['arch']} ({telem['cpu_cores']} cores) | ARM: {telem['arm_freq_mhz']} MHz")
    print(f" Temp / Volts / Power  : {telem['temp_c']} C | {telem['volts_v']} V | ~{telem['power_w_est']} W")
    print(f" RAM Used / Total      : {telem['ram_used_mb']} / {telem['ram_total_mb']} MB")
    print(f" Model Sizes           : Vision INT8 = {vision_mb} MB | Llama INT8 = {llama_mb} MB")
    print(f" Class Accuracy        : {report['class_accuracy_pct']}% ({cls_ok}/{n})")
    print(f" SWM 2026 Compliance   : {report['stream_compliance_pct']}% ({stream_ok}/{n})")
    if haz_tot:
        print(f" Battery Hazard Recall : {report['hazard_recall_pct']}% ({haz_ok}/{haz_tot})")
    print(f" Latency Mean/P50/P95  : {report['latency_mean_ms']} / {report['latency_p50_ms']} / {report['latency_p95_ms']} ms ({fps} FPS)")
    print(f" Mean Conf / Entropy   : {report['mean_confidence']*100:.1f}% | H = {report['mean_entropy']}")
    if llama_result:
        print(f" Llama 3.2 1B INT8     : {llama_result['runtime']} | {llama_result['latency_ms']} ms | {llama_result['tokens_per_sec_est']} tok/s")
        print(f" Llama Advice Sample   : {llama_result['answer']}")
    print(f" Saved JSON Report     : {out_path}")
    print("=" * 78)


def main():
    parser = argparse.ArgumentParser(description="EcoSort-Edge 2.0 Terminal Checker")
    parser.add_argument("--image", type=Path, help="Path to an image to classify and advise on")
    parser.add_argument("--question", type=str, default="", help="Optional question for Llama 3.2 1B INT8")
    parser.add_argument("--batch10", action="store_true", help="Run balanced 10-image benchmark with full report metrics")
    parser.add_argument("--batch30", action="store_true", help="Run all 30 benchmark_30 images in terminal")
    parser.add_argument("--no-llama", action="store_true", help="Skip loading Llama (fast vision+policy check)")
    args = parser.parse_args()

    vision = VisionEngine()
    policy = PolicyGuard()
    llama = None if (args.no_llama or args.batch30) else LlamaExecuTorchEngine()

    if args.batch10:
        run_batch(vision, policy, llama=llama, limit=10)
    elif args.batch30:
        run_batch(vision, policy, llama=None, limit=30)
    elif args.image:
        run_single(vision, policy, llama, args.image, args.question)
    else:
        default_img = SUBMISSION_ROOT / "benchmark_30" / "item_01_battery_01.jpg"
        run_single(vision, policy, llama, default_img, args.question)


if __name__ == "__main__":
    main()
