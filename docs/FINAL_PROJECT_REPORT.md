# EcoSort-Edge 2.0 — Final Project Report

**Air-Gapped Multimodal Waste Segregation & Hazard-Interlock Advisory on Arm Edge SoCs**  
**Hardware Targets:** Raspberry Pi 5 (Arm Cortex-A76, `aarch64`) & Android Armv8-A (`arm64-v8a`)  
**Regulatory Standard:** India Solid Waste Management (SWM) Rules, 2026 (4-Bin Segregation)

---

## 1. Executive Summary

**EcoSort-Edge 2.0** is a 100% offline, privacy-preserving edge AI system that classifies household waste in real time and delivers grounded, multilingual preparation and safety steps with zero cloud dependency. The architecture combines:
1. A compact **ExecuTorch PT2E Static INT8 Vision classifier (`2.41 MB`)** accelerated by **XNNPACK (Arm NEON / SDOT)**,
2. A deterministic **SWM 2026 PolicyGuard** with zero-tolerance fire-hazard and uncertainty interlocks, and
3. An on-device **Llama 3.2 1B-Instruct INT8 (`1,432.96 MB`)** reasoning engine executed natively via **ExecuTorch C++ `llama_main`** (Raspberry Pi 5) and **ExecuTorch `LlmModule`** (Android).

### Key Verified Achievements on Physical Raspberry Pi 5 (`aarch64`, 2.4 GHz)
- **SWM 2026 Statutory Stream Compliance:** **100.0%** (`30/30` on `benchmark_30` and `10/10` on stratified 10-class suite).
- **Lithium Battery / E-Waste Hazard Recall:** **100.0%** (`3/3`) with deterministic Red Bin interception.
- **Vision Speedup vs. PyTorch FP32:** **4.02× – 6.44× faster** (`10.54 ms / 94.88 FPS` across 30 items; `6.58 ms / 152.05 FPS` across 10 items vs. `~42.40 ms` FP32 baseline) with **60% smaller binary** (`2.41 MB` vs. `~6.00 MB`) and **100.00% FP32 decision parity**.
- **On-Device Llama 3.2 1B INT8 (`cpp_llama_main`):** **120.06 – 133.99 tok/s** prompt prefill, **6.83 – 7.33 tok/s** autoregressive decode, **683 ms** Time-to-First-Token (TTFT), and **3.54 – 3.77 s** pure execution latency.
- **Thermal & Power Envelope:** **~4.69 W** (`0.852 V` core), **68.1 – 71.4 °C** (well below `80–85 °C` throttle limit), and **2.65 – 2.86 GB / 8.06 GB** system RAM used.

---

## 2. System Architecture & Dual-Platform Deployment

```
Camera / Image Frame
        │
        ▼
[1] Reticle Focus Crop & Resize (224×224 / 256×256)
        │
        ▼
[2] ExecuTorch Vision INT8 (MobileNetV3-Small, 2.41 MB, XNNPACK NEON/SDOT)
        │  └─ Outputs: Class Probabilities, Confidence, Shannon Entropy (H)
        ▼
[3] Deterministic SWM 2026 PolicyGuard (swm2026.yaml)
        │  ├─ Fire-Hazard Interlock: battery (conf ≥ 0.30) -> Red Bin (HAZARD_INTERCEPTED)
        │  ├─ OOD / Uncertainty Gate: H > 2.00 or conf < 0.30 -> Manual Check
        │  └─ Camera-Free Sanitary Override -> Yellow Bin (SANITARY_PRIVACY_OVERRIDE)
        ▼
[4] Grounded Llama 3.2 1B-Instruct INT8 (1,432.96 MB, XNNPACK)
        │  └─ Generates 2 concise, policy-locked disposal/safety steps
        ▼
Multilingual Output (English · தமிழ் · ಕನ್ನಡ · हिन्दी)
```

| Subsystem | Raspberry Pi 5 (`aarch64`) | Android Mobile (`arm64-v8a`) |
| :--- | :--- | :--- |
| **Vision Runtime** | ExecuTorch Python + XNNPACK (`waste_classifier_int8.pte`) | ExecuTorch Android AAR `Module` (`waste_classifier_int8.pte`) |
| **LLM Runtime** | Native C++ `llama_main` (`llama3_2_1b_int8.pte`) | ExecuTorch `LlmModule` (`llama3_2_1b_int8.pte`) |
| **Policy Engine** | `src/policy_guard.py` + `policy/swm2026.yaml` | `PolicyGuard.kt` + `assets/swm2026.yaml` |
| **User Interfaces** | CLI (`cli_check.py`), Real-Time USB Cam (`pi5_usb_cam_check.py`), Web Console (`ui_dashboard.py`) | Jetpack Compose UI (`Home`, `Scanner`, `Detail`, `Assistant`) + EN/TA/KN/HI switcher |
| **Network Access** | **100% Air-Gapped** | **100% Air-Gapped** (no `INTERNET` permission) |

---

## 3. Methodology

### 3.1 Fine-Grained Taxonomy & SWM 2026 Statutory Mapping
The vision classifier predicts **9 fine-grained waste classes**, which `PolicyGuard` maps deterministically via `policy/swm2026.yaml` into the **4 statutory SWM 2026 household streams**:

| Statutory Bin | Stream ID | Mapped Vision Classes | UI Display & Interlock Behavior |
| :--- | :---: | :--- | :--- |
| **Green Bin (Wet Organic)** | `wet` | `food_waste`, `vegetable_scrap` | Compostable / wet organic stream |
| **Blue Bin (Dry Recyclable)** | `dry` | `cardboard`, `paper`, `plastic_bottle`, `metal_can`, `glass_bottle`, `multi_layer_packaging` | Clean dry recyclable stream |
| **Red Bin (Special-Care / Hazardous)** | `special_care` | `battery` (displayed as **Electronic Waste**) | Zero-tolerance fire-hazard interlock (`conf ≥ 0.30`) |
| **Yellow Bin (Sanitary Waste)** | `sanitary` | `sanitary_item` | Camera-free privacy override button (no image captured) |

### 3.2 Deterministic Safety Guardrails
- **Zero-Tolerance Fire-Hazard Interlock:** Whenever `battery` is predicted with `confidence ≥ 0.30`, PolicyGuard enforces `HAZARD_INTERCEPTED`, locks routing to the **Red Bin**, and prevents any accidental Dry/Wet bin assignment.
- **Shannon Entropy OOD Gate:** Predictions with `entropy > 2.00` or `confidence < 0.30` are flagged for manual verification instead of guessing.
- **Policy-Grounded LLM Prompting:** Llama 3.2 1B INT8 receives the locked `predicted_class`, `assigned_bin`, and `flags` from PolicyGuard, eliminating bin-classification hallucinations.

---

## 4. Optimization Techniques

1. **PT2E Static INT8 Vision Quantization + XNNPACK Delegation:**  
   MobileNetV3-Small was calibrated and exported via PyTorch 2 Export (`PT2E`) static INT8 quantization (`waste_classifier_int8.pte`, **2.41 MB**) with **XNNPACK** Arm NEON/SDOT kernel lowering, cutting binary size by **60%** and accelerating inference **4.0×–6.4×** over FP32.
2. **Llama 3.2 1B Dynamic INT8 Selection (`0.9880` Cosine Fidelity):**  
   Four quantization variants were ablated (`BF16`, `8da4w G=128`, `8da4w G=32`, and `XNNPACK PT2E Dynamic INT8`). While 4-bit `8da4w` suffered `0.0%–33.3%` top-1 agreement drift, **Dynamic INT8 (`1,433.0 MB`)** preserved **0.9880 cosine similarity** and **100.0% top-1 agreement** with BF16.
3. **On-Device C++ `llama_main` Compilation (ExecuTorch 1.6.0):**  
   Compiled natively on Raspberry Pi 5 (`aarch64`) with `-DEXECUTORCH_BUILD_XNNPACK=ON` (KleidiAI + NEON/SDOT kernels), `-DEXECUTORCH_BUILD_KERNELS_OPTIMIZED=ON`, `-DEXECUTORCH_BUILD_KERNELS_QUANTIZED=ON`, and `-DEXECUTORCH_BUILD_KERNELS_LLM=ON`.
4. **Reticle Focus Crop:**  
   Center-crops the live camera frame to the target reticle before resizing, reducing background noise and improving confidence on real-world items.
5. **Bounded Prompt & Token Budgets:**  
   Concise grounded prompts (`82` prompt tokens) and a strict `44–72` token generation limit keep Llama responses concise (`2` safety/prep steps in **3.54 – 3.77 s** pure execution).
6. **Memory Pre-Warming & LRU Response Caching:**  
   Android `LlamaAdvisor` pre-warms the `1.43 GB` model on launch in a background coroutine, and `LlamaExecuTorchEngine` caches up to `64` `(item, bin, question, language)` tuples in an `OrderedDict` LRU cache.

---

## 5. FP32 vs. ExecuTorch INT8 Comparison & Full Metrics

### 5.1 Vision Classifier: PyTorch FP32 (`.pth`) vs. ExecuTorch PT2E INT8 (`.pte`)
| Metric | PyTorch FP32 (`.pth`) *(Baseline)* | ExecuTorch INT8 (`.pte`) *(Laptop x86)* | ExecuTorch INT8 (`.pte`) *(Pi 5 `batch30`)* | ExecuTorch INT8 (`.pte`) *(Pi 5 `batch10`)* |
| :--- | :---: | :---: | :---: | :---: |
| **Model Binary Size** | `~6.00 MB` | **`2.41 MB`** (`-60%`) | **`2.41 MB`** (`-60%`) | **`2.41 MB`** (`-60%`) |
| **FP32 Decision Parity** | `100.00%` | **`100.00%`** (`Cosine = 0.9902`) | **`100.00%`** | **`100.00%`** |
| **Mean Inference Latency** | `~42.40 ms` | `9.49 ms` | **`10.54 ms`** (`4.02×` faster) | **`6.58 ms`** (`6.44×` faster) |
| **P50 / P95 / Min / Max Latency** | — | `— / 10.71 / — / — ms` | **`9.88 / 15.34 / 5.11 / 20.21 ms`** | **`5.24 / 10.52 / 4.37 / 11.44 ms`** |
| **Throughput (FPS)** | `~23.6 FPS` | `105.37 FPS` | **`94.88 FPS`** | **`152.05 FPS`** |
| **Fine-Grained Class Accuracy** | `80.0%` | `80.0%` (`24/30`) | **`80.0%`** (`24/30`) | **`80.0%`** (`8/10`) |
| **SWM 2026 Statutory Compliance** | `100.0%` | `100.0%` (`30/30`) | **`100.0%`** (`30/30`) | **`100.0%`** (`10/10`) |
| **Battery Fire-Hazard Recall** | `100.0%` | `100.0%` (`3/3`) | **`100.0%`** (`3/3`) | **`100.0%`** (`2/2`) |
| **Mean Confidence / Entropy (`H`)** | — | — | **`87.06%` / `H = 0.5231`** | **`88.49%` / `H = 0.4815`** |
| **Active SoC Power** | `~6.40 W` | `N/A` (Windows) | **`~4.69 W`** | **`~4.69 W`** |

> **Why SWM 2026 Compliance is 100.0% while Fine-Grained Class Accuracy is 80.0%:** All 6 fine-grained confusions in `benchmark_30` occur strictly *within the same statutory bin* (`multi_layer_packaging` → `metal_can` in Blue Bin, `plastic_bottle` → `cardboard` in Blue Bin, and `vegetable_scrap` → `food_waste` in Green Bin). Zero hazardous or wet/dry misroutings occurred.

---

### 5.2 Llama 3.2 1B-Instruct: BF16 Baseline vs. Quantized ExecuTorch Ablation
Measured in [llama_quantization_ablation.json](../benchmarks/llama_quantization_ablation.json):

| Variant | `.pte` Size | Logit Cosine vs. BF16 | Top-1 Token Agreement | Decision |
| :--- | :---: | :---: | :---: | :--- |
| **BF16 Baseline** | Unquantized | `1.0000` | `100.0%` | Reference baseline |
| **`8da4w` (`G=128`)** | `765.3 MB` | `0.9059` | `0.0%` | Rejected (severe output drift) |
| **`8da4w` (`G=32`)** | `820.5 MB` | `0.9208` | `33.3%` | Rejected (moderate drift) |
| **`INT8 (XNNPACK PT2E Dynamic)`** | **`1,432.96 MB`** | **`0.9880`** | **`100.0%`** | **Selected (near-BF16 fidelity + C++ XNNPACK)** |

---

### 5.3 On-Device Llama 3.2 1B INT8 Metrics on Physical Raspberry Pi 5 (`PyTorchObserver`)
Measured directly via ExecuTorch's native C++ `llama_main` instrumentation (`item_01_battery_01.jpg`, Red Bin hazard advice):

| Metric | `--batch10` Run (Cold Load) | Single-Item CLI Run (Warm Page Cache) |
| :--- | :---: | :---: |
| **Prompt Prefill Speed (`prefill_token_per_sec`)** | **120.06 tok/s** (`82` prompt tokens) | **133.99 tok/s** |
| **Token Decode Speed (`decode_token_per_sec`)** | **7.33 tok/s** (`21` generated tokens) | **6.83 tok/s** |
| **Time to First Token (TTFT)** | **683 ms** (`0.68 s`) | — |
| **Pure Model Execution (`aggregate_model_execution_time_ms`)** | **3,539 ms** (`3.54 s`) | **3,773 ms** (`3.77 s`) |
| **Sampling Overhead (`aggregate_sampling_time_ms`)** | **3 ms** | **3 ms** |
| **Model Load Time (`1,432.96 MB` `.pte`)** | **17,061 ms** (`17.06 s`) | `~10,436 ms` |
| **Total Subprocess Wall Time** | **20,764.8 ms** (`0.92 tok/s` incl. load) | **14,209.1 ms** (`1.33 tok/s` incl. load) |
| **Generated Safety Advice** | *"1. Handle with care to avoid electrical shock. 2. Secure terminals with tape to prevent short circuits."* | Same safety-grounded steps |

---

### 5.4 Per-Item Breakdown (`pi5_cli_10_report.json` on Pi 5)
| Sample ID | True Class | Predicted Class | Confidence | Entropy (`H`) | Statutory Bin | Latency (ms) | Stream Match |
| :--- | :--- | :--- | :---: | :---: | :--- | :---: | :---: |
| `item_01` | `battery` | `battery` | 92.2% | 0.4186 | `special_care` (Red) | 11.44 | OK |
| `item_02` | `battery` | `battery` | 98.0% | 0.1382 | `special_care` (Red) | 6.03 | OK |
| `item_04` | `cardboard` | `cardboard` | 78.6% | 0.9030 | `dry` (Blue) | 9.40 | OK |
| `item_07` | `food_waste` | `food_waste` | 99.2% | 0.0597 | `wet` (Green) | 5.19 | OK |
| `item_11` | `glass_bottle` | `glass_bottle` | 97.6% | 0.1511 | `dry` (Blue) | 5.24 | OK |
| `item_14` | `metal_can` | `metal_can` | 99.4% | 0.0426 | `dry` (Blue) | 4.94 | OK |
| `item_17` | `multi_layer_packaging` | `multi_layer_packaging` | 95.0% | 0.2839 | `dry` (Blue) | 5.24 | OK |
| `item_21` | `paper` | `paper` | 77.0% | 0.8525 | `dry` (Blue) | 4.37 | OK |
| `item_24` | `plastic_bottle` | `cardboard` | 62.7% | 1.3164 | `dry` (Blue) | 5.01 | OK |
| `item_28` | `vegetable_scrap` | `food_waste` | 85.0% | 0.6490 | `wet` (Green) | 8.91 | OK |

---

## 6. Hardware Utilization & SoC Telemetry (Raspberry Pi 5)

Captured via `vcgencmd` and `/proc/meminfo` during active inference benchmarks:

| Hardware Telemetry Metric | 10-Item Benchmark + Llama | 30-Item Benchmark |
| :--- | :---: | :---: |
| **Architecture / CPU** | `aarch64` / 4× Arm Cortex-A76 | `aarch64` / 4× Arm Cortex-A76 |
| **ARM CPU Frequency (`measure_clock arm`)** | `2400.0 MHz` | `2400.0 MHz` |
| **SoC Temperature (`measure_temp`)** | `68.1 °C` | `71.4 °C` |
| **Core Voltage (`measure_volts core`)** | `0.852 V` | `0.852 V` |
| **Estimated SoC Power (`5.5 × V_core`)** | **`~4.69 W`** | **`~4.69 W`** |
| **RAM Used / Total** | `2,857.5 / 8,062.3 MB` (`35.4%`) | `2,652.7 / 8,062.3 MB` (`32.9%`) |
| **Storage Footprint (Vision + Llama INT8)** | `2.41 MB + 1,432.96 MB` | `2.41 MB + 1,432.96 MB` |

---

## 7. Supporting Evidence Inventory (`arm_submission/evidence/`)

- **Machine-Readable Pi 5 Reports:**
  - [pi5_cli_10_report.json](../evidence/pi5_cli_10_report.json) — 10-class Vision + `llama_main` `PyTorchObserver` + `vcgencmd` telemetry
  - [pi5_cli_30_report.json](../evidence/pi5_cli_30_report.json) — Full 30-item Vision + PolicyGuard + `vcgencmd` telemetry
- **Hardware & Terminal Captures:**
  - [WhatsApp Image 2026-11-25 at 8.53.18 PM.jpeg](../evidence/WhatsApp%20Image%202026-11-25%20at%208.53.18%20PM.jpeg) — Pi 5 rig, USB webcam, `cli_check.py --batch10`, single-item Llama output, and `ui_dashboard.py` server live on `:8080`
  - [WhatsApp Image 2026-11-25 at 8.53.18 PM (1).jpeg](../evidence/WhatsApp%20Image%202026-11-25%20at%208.53.18%20PM%20(1).jpeg) — Workstation view during Pi 5 CLI & Dashboard verification
  - [WhatsApp Image 2026-11-25 at 8.53.18 PM (2).jpeg](../evidence/WhatsApp%20Image%202026-11-25%20at%208.53.18%20PM%20(2).jpeg) — Alternate angle of active-cooled Pi 5 setup and live metrics
- **Live Demo & Inference Videos:**
  - [WhatsApp Video 2026-11-25 at 8.52.01 PM.mp4](../evidence/WhatsApp%20Video%202026-11-25%20at%208.52.01%20PM.mp4) (`10.1 MB`)
  - [WhatsApp Video 2026-11-25 at 8.52.05 PM.mp4](../evidence/WhatsApp%20Video%202026-11-25%20at%208.52.05%20PM.mp4) (`2.17 MB`)
  - [WhatsApp Video 2026-11-25 at 8.53.17 PM.mp4](../evidence/WhatsApp%20Video%202026-11-25%20at%208.53.17%20PM.mp4) (`1.25 MB`)
  - [WhatsApp Video 2026-11-25 at 8.53.17 PM (1).mp4](../evidence/WhatsApp%20Video%202026-11-25%20at%208.53.17%20PM%20(1).mp4) (`9.28 MB`)
  - [WhatsApp Video 2026-11-25 at 8.53.17 PM (2).mp4](../evidence/WhatsApp%20Video%202026-11-25%20at%208.53.17%20PM%20(2).mp4) (`6.19 MB`)
  - [WhatsApp Video 2026-11-25 at 8.53.18 PM.mp4](../evidence/WhatsApp%20Video%202026-11-25%20at%208.53.18%20PM.mp4) (`17.95 MB`)
