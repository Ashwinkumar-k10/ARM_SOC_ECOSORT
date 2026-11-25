"""
arm_submission/src/vision_engine.py
ExecuTorch INT8 Vision Engine (MobileNetV3-Small) with foreground saliency
localization for Raspberry Pi 5 / Arm CPU.
"""

import time
from pathlib import Path
from typing import Dict, Optional, Tuple
import cv2
import numpy as np
import torch
from PIL import Image
from torchvision import transforms

DEFAULT_MODEL_PATH = Path(__file__).resolve().parent.parent / "models" / "waste_classifier_int8.pte"

CLASSES = [
    "battery",
    "cardboard",
    "food_waste",
    "glass_bottle",
    "metal_can",
    "multi_layer_packaging",
    "paper",
    "plastic_bottle",
    "vegetable_scrap",
]


class VisionEngine:
    """Runs MobileNetV3-Small INT8 through ExecuTorch Runtime."""

    def __init__(self, model_path: Optional[Path] = None):
        self.model_path = Path(model_path) if model_path else DEFAULT_MODEL_PATH
        if not self.model_path.exists():
            raise FileNotFoundError(f"ExecuTorch vision model not found: {self.model_path}")

        from executorch.runtime import Runtime

        t0 = time.perf_counter()
        self.runtime = Runtime.get()
        self.program = self.runtime.load_program(str(self.model_path))
        self.method = self.program.load_method("forward")
        self.load_latency_ms = (time.perf_counter() - t0) * 1000.0

        self.transform = transforms.Compose([
            transforms.Resize((256, 256)),
            transforms.ToTensor(),
            transforms.Normalize(mean=[0.485, 0.456, 0.406], std=[0.229, 0.224, 0.225]),
        ])

    @staticmethod
    def localize_foreground(frame_bgr: np.ndarray, white_bg_mode: bool = True) -> Tuple[int, int, int, int]:
        """Extract foreground object bounding box in <1.2 ms."""
        h, w, _ = frame_bgr.shape
        gray = cv2.cvtColor(frame_bgr, cv2.COLOR_BGR2GRAY)

        if white_bg_mode or float(np.median(gray)) > 175.0:
            mask = (gray < 220) & (gray > 18)
            coords = np.column_stack(np.where(mask))
            if len(coords) > (w * h * 0.015):
                y0, x0 = coords.min(axis=0)
                y1, x1 = coords.max(axis=0)
                pad_x = int(0.06 * (x1 - x0))
                pad_y = int(0.06 * (y1 - y0))
                x = max(0, x0 - pad_x)
                y = max(0, y0 - pad_y)
                bw = min(w - x, (x1 - x0) + 2 * pad_x)
                bh = min(h - y, (y1 - y0) + 2 * pad_y)
                if bw > 30 and bh > 30:
                    return int(x), int(y), int(bw), int(bh)

        blurred = cv2.GaussianBlur(gray, (5, 5), 0)
        edges = cv2.Canny(blurred, 45, 140)
        dilated = cv2.dilate(edges, np.ones((5, 5), np.uint8), iterations=2)
        contours, _ = cv2.findContours(dilated, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
        if contours:
            largest = max(contours, key=cv2.contourArea)
            if cv2.contourArea(largest) > (w * h * 0.025):
                x, y, bw, bh = cv2.boundingRect(largest)
                return int(x), int(y), int(bw), int(bh)

        return 0, 0, w, h

    def classify_frame(self, frame_bgr: np.ndarray, use_saliency: bool = True) -> Dict:
        """Run saliency localization + ExecuTorch INT8 forward pass on BGR image."""
        t_total = time.perf_counter()
        if use_saliency:
            bx, by, bw, bh = self.localize_foreground(frame_bgr)
            crop = frame_bgr[by : by + bh, bx : bx + bw]
            if crop.size == 0:
                crop = frame_bgr
        else:
            h, w, _ = frame_bgr.shape
            bx, by, bw, bh = 0, 0, w, h
            crop = frame_bgr

        rgb = cv2.cvtColor(crop, cv2.COLOR_BGR2RGB)
        tensor = self.transform(Image.fromarray(rgb)).unsqueeze(0).contiguous()

        t_infer = time.perf_counter()
        outputs = self.method.execute([tensor])
        infer_ms = (time.perf_counter() - t_infer) * 1000.0

        logits = outputs[0]
        if not isinstance(logits, torch.Tensor):
            logits = torch.tensor(logits)
        probs = torch.softmax(logits, dim=1).cpu().numpy()[0]

        pred_idx = int(np.argmax(probs))
        conf = float(probs[pred_idx])
        entropy = float(-np.sum(probs * np.log(probs + 1e-12)))
        total_ms = (time.perf_counter() - t_total) * 1000.0

        return {
            "predicted_class": CLASSES[pred_idx],
            "confidence": conf,
            "entropy": entropy,
            "bbox": [bx, by, bw, bh],
            "inference_ms": round(infer_ms, 2),
            "pipeline_ms": round(total_ms, 2),
            "probs": {CLASSES[i]: round(float(probs[i]), 4) for i in range(len(CLASSES))},
        }
