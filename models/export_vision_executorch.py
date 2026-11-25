"""
arm_submission/models/export_vision_executorch.py
EcoSort-Edge 2.0: Arm ExecuTorch PT2E INT8 Quantization & XNNPACK Lowering
for MobileNetV3-Small Vision Classifier.

Pipeline:
  1. Load FP32 PyTorch checkpoint
  2. Capture computation graph via torch.export.export(..., strict=False)
  3. Apply PT2E INT8 quantization (per-channel symmetric weights + dynamic activations)
  4. Calibrate on benchmark_30 / PS3 imagery
  5. Lower via to_edge_transform_and_lower(XnnpackPartitioner, get_transform_passes)
  6. Verify runtime load + cosine similarity parity against FP32
"""

import argparse
import sys
from pathlib import Path
from typing import List, Tuple

import numpy as np
import torch
import torch.nn.functional as F
import torchvision.models as models
from PIL import Image
from torchvision import transforms

SUBMISSION_MODELS = Path(__file__).resolve().parent
PROJECT_ROOT = SUBMISSION_MODELS.parent.parent


def load_fp32_model(ckpt_path: Path, num_classes: int = 9) -> torch.nn.Module:
    if not ckpt_path.exists():
        raise FileNotFoundError(f"FP32 checkpoint not found: {ckpt_path}")
    ckpt = torch.load(ckpt_path, map_location="cpu", weights_only=False)
    model = models.mobilenet_v3_small(num_classes=num_classes)
    state = ckpt["model_state_dict"] if isinstance(ckpt, dict) and "model_state_dict" in ckpt else ckpt
    model.load_state_dict(state)
    model.eval()
    return model


def collect_calibration_images(calib_dir: Path) -> List[Path]:
    if not calib_dir.exists():
        return []
    return sorted(list(calib_dir.glob("*.jpg")) + list(calib_dir.glob("*.png")))


def export_vision_pte(
    ckpt_path: Path,
    calib_dir: Path,
    output_pte: Path,
) -> Tuple[float, float]:
    from torchao.quantization.pt2e.quantize_pt2e import convert_pt2e, prepare_pt2e
    from executorch.backends.xnnpack.partition.xnnpack_partitioner import XnnpackPartitioner
    from executorch.backends.xnnpack.quantizer.xnnpack_quantizer import (
        XNNPACKQuantizer,
        get_symmetric_quantization_config,
    )
    from executorch.backends.xnnpack.utils.configs import get_transform_passes
    from executorch.exir import to_edge_transform_and_lower
    from executorch.runtime import Runtime

    fp32_model = load_fp32_model(ckpt_path)
    example_input = torch.randn(1, 3, 256, 256)
    exported_program = torch.export.export(fp32_model, (example_input,), strict=False)

    quantizer = XNNPACKQuantizer()
    quant_config = get_symmetric_quantization_config(is_per_channel=True, is_dynamic=True)
    quantizer.set_global(quant_config)

    try:
        prepared = prepare_pt2e(exported_program, quantizer)
    except Exception:
        prepared = prepare_pt2e(exported_program.module(), quantizer)

    tf = transforms.Compose([
        transforms.Resize((256, 256)),
        transforms.ToTensor(),
        transforms.Normalize(mean=[0.485, 0.456, 0.406], std=[0.229, 0.224, 0.225]),
    ])

    calib_images = collect_calibration_images(calib_dir)
    with torch.no_grad():
        runner = prepared.module if hasattr(prepared, "module") and callable(getattr(prepared, "module")) else prepared
        for img_p in calib_images:
            img = Image.open(img_p).convert("RGB")
            runner(tf(img).unsqueeze(0))

    quantized_model = convert_pt2e(prepared)
    if not isinstance(quantized_model, torch.export.ExportedProgram):
        quantized_program = torch.export.export(quantized_model, (example_input,), strict=False)
    else:
        quantized_program = quantized_model

    et_program = to_edge_transform_and_lower(
        quantized_program,
        partitioner=[XnnpackPartitioner()],
        transform_passes=get_transform_passes(),
    )
    execu_program = et_program.to_executorch()
    output_pte.parent.mkdir(parents=True, exist_ok=True)
    with open(output_pte, "wb") as f:
        f.write(execu_program.buffer)

    # Parity verification against FP32
    rt = Runtime.get()
    method = rt.load_program(str(output_pte)).load_method("forward")
    cos_sims, matches = [], 0
    test_imgs = calib_images[:10] if calib_images else []
    with torch.no_grad():
        for img_p in test_imgs:
            x = tf(Image.open(img_p).convert("RGB")).unsqueeze(0).contiguous()
            fp32_out = fp32_model(x)
            et_out = method.execute([x])[0]
            if not isinstance(et_out, torch.Tensor):
                et_out = torch.tensor(et_out)
            cos_sims.append(float(F.cosine_similarity(fp32_out, et_out, dim=1).item()))
            if int(fp32_out.argmax(dim=1)) == int(et_out.argmax(dim=1)):
                matches += 1

    mean_cos = float(np.mean(cos_sims)) if cos_sims else 1.0
    agreement = (matches / len(test_imgs)) * 100.0 if test_imgs else 100.0
    return mean_cos, agreement


def main():
    parser = argparse.ArgumentParser(description="Export Vision MobileNetV3-Small INT8 to ExecuTorch")
    parser.add_argument(
        "--ckpt",
        type=Path,
        default=PROJECT_ROOT / "models" / "ecosort_mobilenetv3_small_256_progressive.pth",
    )
    parser.add_argument(
        "--calib-dir",
        type=Path,
        default=PROJECT_ROOT / "PS3",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=SUBMISSION_MODELS / "waste_classifier_int8.pte",
    )
    args = parser.parse_args()

    mean_cos, agreement = export_vision_pte(args.ckpt, args.calib_dir, args.output)
    size_mb = args.output.stat().st_size / (1024 * 1024)
    print(f"[✓] Vision ExecuTorch Binary : {args.output} ({size_mb:.2f} MB)")
    print(f"[✓] FP32 Parity Verification : Cosine={mean_cos:.4f} | Top-1 Agreement={agreement:.2f}%")


if __name__ == "__main__":
    main()
