"""
arm_submission/src/pi5_usb_cam_check.py
Real-Time USB Webcam Inference Checker for Raspberry Pi 5 (Arm Cortex-A76).
Runs ExecuTorch INT8 Vision -> SWM 2026 PolicyGuard (+ optional Llama 3.2 1B INT8)
in real time from a connected USB camera.

Supports:
  - Desktop live preview window (when DISPLAY is set): press L for Llama advice, Q to quit
  - Headless SSH terminal mode (--headless, or auto if no DISPLAY): live FPS + SoC telemetry

Usage:
  python src/pi5_usb_cam_check.py
  python src/pi5_usb_cam_check.py --cam 0 --headless
  python src/pi5_usb_cam_check.py --cam 0 --with-llama
"""

import argparse
import os
import subprocess
import sys
import time
from pathlib import Path
import cv2

SRC_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(SRC_DIR))

from policy_guard import PolicyGuard
from vision_engine import VisionEngine
from llama_engine import LlamaExecuTorchEngine


STREAM_BGR = {
    "wet": (74, 138, 22),          # Green
    "dry": (216, 108, 29),         # Blue
    "sanitary": (11, 158, 245),    # Yellow/Amber
    "special_care": (28, 28, 183), # Red
    "reject": (107, 114, 128),     # Gray
}


def format_class_name(raw: str) -> str:
    if raw == "battery":
        return "Electronic Waste"
    return raw.replace("_", " ").title()


def read_pi_temp_volts() -> str:
    temp = volts = "N/A"
    try:
        t = subprocess.check_output(["vcgencmd", "measure_temp"], text=True, timeout=0.5).strip()
        temp = t.split("=")[1]
    except Exception:
        pass
    try:
        v = subprocess.check_output(["vcgencmd", "measure_volts", "core"], text=True, timeout=0.5).strip()
        volts = v.split("=")[1]
    except Exception:
        pass
    return f"Temp={temp} Volts={volts}"


def main():
    parser = argparse.ArgumentParser(description="EcoSort-Edge 2.0 Real-Time USB Webcam Checker (Pi 5)")
    parser.add_argument("--cam", type=int, default=0, help="USB camera index (default: 0)")
    parser.add_argument("--width", type=int, default=640)
    parser.add_argument("--height", type=int, default=480)
    parser.add_argument("--headless", action="store_true", help="Force terminal-only output (no cv2.imshow)")
    parser.add_argument("--with-llama", action="store_true", help="Enable Llama 3.2 1B INT8 advice (press L or on class change)")
    parser.add_argument("--interval", type=float, default=0.5, help="Terminal print interval in seconds for headless mode")
    args = parser.parse_args()

    headless = args.headless or (sys.platform.startswith("linux") and not os.environ.get("DISPLAY"))

    print("[*] Loading ExecuTorch INT8 Vision & SWM 2026 PolicyGuard...")
    vision = VisionEngine()
    policy = PolicyGuard()
    llama = LlamaExecuTorchEngine() if args.with_llama else None

    cap = cv2.VideoCapture(args.cam)
    cap.set(cv2.CAP_PROP_FRAME_WIDTH, args.width)
    cap.set(cv2.CAP_PROP_FRAME_HEIGHT, args.height)
    if not cap.isOpened():
        raise RuntimeError(f"Could not open USB camera index {args.cam}. Check `ls /dev/video*` on Pi 5.")

    print(f"[✓] USB Camera {args.cam} opened ({int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))}x{int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))}).")
    print(f"[*] Mode: {'Headless SSH Terminal (Ctrl+C to stop)' if headless else 'Desktop Preview (Q=quit, L=Llama advice)'}")

    last_print = 0.0
    last_llama_cls = None
    fps_smooth = 0.0

    try:
        while True:
            t0 = time.perf_counter()
            ok, frame = cap.read()
            if not ok or frame is None:
                continue

            vis = vision.classify_frame(frame)
            dec = policy.evaluate(vis["predicted_class"], vis["confidence"], vis["entropy"])
            dt = max(time.perf_counter() - t0, 1e-4)
            inst_fps = 1.0 / dt
            fps_smooth = inst_fps if fps_smooth == 0.0 else (0.85 * fps_smooth + 0.15 * inst_fps)

            cls_label = format_class_name(dec["predicted_class"])
            now = time.time()

            if headless:
                if now - last_print >= args.interval:
                    soc = read_pi_temp_volts()
                    print(
                        f"[LIVE] {cls_label:18s} | {dec['bin_name']:38s} | "
                        f"Conf={dec['confidence']*100:5.1f}% H={dec['entropy']:.2f} | "
                        f"{vis['inference_ms']:5.1f} ms ({fps_smooth:4.1f} FPS) | {soc}"
                    )
                    last_print = now
                    if llama is not None and dec["predicted_class"] != last_llama_cls and dec["confidence"] >= 0.40:
                        adv = llama.generate(decision=dec, user_question=None)
                        print(f"       Llama ({adv['latency_ms']} ms | {adv['tokens_per_sec_est']} tok/s): {adv['answer']}")
                        last_llama_cls = dec["predicted_class"]
            else:
                h, w = frame.shape[:2]
                box_color = STREAM_BGR.get(dec["stream"], (255, 255, 255))
                x1, y1, x2, y2 = vis["bbox"]
                cv2.rectangle(frame, (x1, y1), (x2, y2), box_color, 3)
                cv2.rectangle(frame, (0, 0), (w, 72), box_color, -1)
                cv2.putText(
                    frame,
                    f"{cls_label} -> {dec['bin_name']}",
                    (12, 28),
                    cv2.FONT_HERSHEY_SIMPLEX,
                    0.72,
                    (255, 255, 255),
                    2,
                )
                cv2.putText(
                    frame,
                    f"Conf: {dec['confidence']*100:.1f}% | {vis['inference_ms']:.1f} ms | {fps_smooth:.1f} FPS | [L] Llama [Q] Quit",
                    (12, 56),
                    cv2.FONT_HERSHEY_SIMPLEX,
                    0.52,
                    (255, 255, 255),
                    1,
                )
                cv2.imshow("EcoSort-Edge 2.0 Pi 5 Live USB Cam", frame)
                key = cv2.waitKey(1) & 0xFF
                if key in (ord("q"), ord("Q")):
                    break
                if key in (ord("l"), ord("L")):
                    if llama is None:
                        llama = LlamaExecuTorchEngine()
                    adv = llama.generate(decision=dec, user_question=None)
                    print(f"\n[Llama 3.2 INT8 | {adv['latency_ms']} ms | {adv['tokens_per_sec_est']} tok/s] {adv['answer']}\n")
    except KeyboardInterrupt:
        print("\n[*] Stopped USB camera stream.")
    finally:
        cap.release()
        if not headless:
            cv2.destroyAllWindows()


if __name__ == "__main__":
    main()
