"""
arm_submission/benchmarks/benchmark_pi5_soc.py
Evaluates ExecuTorch INT8 Vision (waste_classifier_int8.pte) + PolicyGuard
across all 30 benchmark_30 images against ground_truth.json, recording
accuracy, statutory SWM 2026 compliance, battery hazard recall, latency,
and Raspberry Pi 5 hardware telemetry.
"""

import json
import subprocess
import sys
from pathlib import Path
import cv2
import numpy as np

SUBMISSION_ROOT = Path(__file__).resolve().parent.parent
PROJECT_ROOT = SUBMISSION_ROOT.parent
sys.path.insert(0, str(SUBMISSION_ROOT / "src"))

from policy_guard import PolicyGuard
from vision_engine import VisionEngine


def read_pi_telemetry():
    info = {"platform": sys.platform, "temp_c": None, "volts_v": None, "power_w_est": None}
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
    return info


def main():
    b30_dir = (
        SUBMISSION_ROOT / "benchmark_30"
        if (SUBMISSION_ROOT / "benchmark_30").exists()
        else PROJECT_ROOT / "benchmark_30"
    )
    gt_path = b30_dir / "ground_truth.json"
    if not gt_path.exists():
        raise FileNotFoundError(f"ground_truth.json not found in {b30_dir}")

    gt_items = json.loads(gt_path.read_text(encoding="utf-8"))
    vision = VisionEngine()
    policy = PolicyGuard()

    rows = []
    latencies = []
    cls_correct = 0
    stream_correct = 0
    hazard_total = 0
    hazard_caught = 0

    for item in gt_items:
        img_path = b30_dir / item["filename"]
        frame = cv2.imread(str(img_path))
        vis = vision.classify_frame(frame)
        dec = policy.evaluate(vis["predicted_class"], vis["confidence"], vis["entropy"])
        latencies.append(vis["inference_ms"])

        c_ok = vis["predicted_class"] == item["true_class"]
        s_ok = dec["stream"] == item["statutory_stream"]
        if c_ok:
            cls_correct += 1
        if s_ok:
            stream_correct += 1
        if item["statutory_stream"] == "special_care":
            hazard_total += 1
            if dec["stream"] == "special_care":
                hazard_caught += 1

        rows.append({
            "sample_id": item["sample_id"],
            "filename": item["filename"],
            "true_class": item["true_class"],
            "true_stream": item["statutory_stream"],
            "predicted_class": vis["predicted_class"],
            "confidence": round(vis["confidence"], 4),
            "entropy": round(vis["entropy"], 4),
            "stream": dec["stream"],
            "bin_name": dec["bin_name"],
            "status": dec["status"],
            "class_match": c_ok,
            "stream_match": s_ok,
            "inference_ms": vis["inference_ms"],
        })
        mark = "OK" if s_ok else "MISS"
        print(
            f"[{mark:4s}] {item['filename']:34s} | Pred: {vis['predicted_class']:21s} ({vis['confidence']*100:5.1f}%) "
            f"| Bin: {dec['stream']:12s} | {vis['inference_ms']:5.2f} ms"
        )

    total = len(rows)
    report = {
        "model": vision.model_path.name,
        "dataset": "benchmark_30",
        "samples": total,
        "class_accuracy_pct": round((cls_correct / total) * 100.0, 2),
        "statutory_compliance_pct": round((stream_correct / total) * 100.0, 2),
        "battery_hazard_recall_pct": round((hazard_caught / hazard_total) * 100.0, 2) if hazard_total else None,
        "load_latency_ms": round(vision.load_latency_ms, 2),
        "avg_inference_ms": round(float(np.mean(latencies)), 2),
        "p95_inference_ms": round(float(np.percentile(latencies, 95)), 2),
        "telemetry": read_pi_telemetry(),
        "results": rows,
    }

    out_path = SUBMISSION_ROOT / "benchmarks" / "vision_benchmark_30.json"
    out_path.write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(f"\n[✓] Benchmark saved: {out_path}")
    print(
        f"    Class Acc: {report['class_accuracy_pct']}% | SWM 2026 Compliance: {report['statutory_compliance_pct']}% "
        f"| Battery Recall: {report['battery_hazard_recall_pct']}% | Avg Infer: {report['avg_inference_ms']} ms"
    )


if __name__ == "__main__":
    main()
