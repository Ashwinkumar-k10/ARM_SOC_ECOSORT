# EcoSort-Edge 2.0 — Standard 5-Step Arm ExecuTorch Engineering Architecture

**Challenge:** Bharat AI-SoC Challenge 2026–27 (PS2: Smart Recycling & Waste-Segregation Assistant)  
**Target Hardware:** Raspberry Pi 5 (Quad-Core Arm Cortex-A76 @ 2.4 GHz) & Arm-based Android Smartphones  

---

## Step 1: Model & Statutory Dataset Preparation
- **Vision Backbone:** `MobileNetV3-Small` (9 municipal waste classes trained via progressive `256×256` resizing).
- **LLM Backbone:** Meta `Llama-3.2-1B-Instruct` (`original/` checkpoint + `tokenizer.model`).
- **Statutory Policy:** `arm_submission/policy/swm2026.yaml` codifying India's MoEFCC SWM Rules 2026 (Wet, Dry, Sanitary, Special-Care + Reject).

## Step 2: Graph Capture & Quantization
- **Vision (`arm_submission/models/export_vision_executorch.py`):** `torch.export.export(..., strict=False)` → `XNNPACKQuantizer` (`is_per_channel=True, is_dynamic=True`) → calibrated on `PS3/` → `convert_pt2e()`.
- **Llama (`arm_submission/models/export_llama_executorch.py`):** `export_llama` with `8da4w` (`G=32`, `4,32` embeddings) and `--pt2e_quantize xnnpack_dynamic` (compact INT8) + KV-cache + SDPA.

## Step 3: Arm XNNPACK / KleidiAI Lowering
- Lowered via `to_edge_transform_and_lower(..., partitioner=[XnnpackPartitioner()], transform_passes=get_transform_passes())` (Vision) and `-X` XNNPACK delegate (Llama).
- Exploits Armv8.2-A **NEON `sdot`** vector instructions on Cortex-A76 and mobile Cortex-A CPUs.

## Step 4: On-Device Orchestration & Safety Guardrails
- **`arm_submission/src/vision_engine.py`:** `<1.2 ms` foreground saliency + ExecuTorch INT8 classification.
- **`arm_submission/src/policy_guard.py`:** Decision-Path Isolation (0.30 battery fire-hazard interlock + Shannon Entropy $H > 2.00$ OOD gate). Llama **cannot** override statutory bin routing.
- **`arm_submission/src/llama_engine.py`:** Grounded Llama 3.2 ExecuTorch runner with LRU caching of real generated responses for instant repeat kiosk queries.
- **`arm_submission/src/pi5_edge_server.py`:** Air-gapped local HTTP/JSON server (`/api/infer`, `/api/chat`, `/api/status`, `/api/sanitary-override`).

## Step 5: Empirical Verification & Hardware Ablation
- **Vision Benchmark (`arm_submission/benchmarks/benchmark_pi5_soc.py`):** `2.41 MB` `.pte`, `100.00%` FP32 decision agreement (`Cosine = 0.9902`), `9.35 ms` avg laptop CPU / `5.09 ms` Pi 5 Cortex-A76.
- **Llama Ablation (`arm_submission/benchmarks/ablate_llama_quantization.py`):** Compares BF16, `8da4w (G=128)`, `8da4w (G=32)`, and compact `INT8` (`xnnpack_dynamic`) with zero broken or mocked results.
