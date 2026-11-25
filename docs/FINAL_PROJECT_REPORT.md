# EcoSort-Edge 2.0 — Final Project Report

**Air-Gapped Multimodal Waste Segregation & Hazard-Interlock Advisory on Arm Edge SoCs**  
**Targets:** Raspberry Pi 5 (Arm Cortex-A76, `aarch64`) & Android Armv8-A (`arm64-v8a`)  
**Compliance Standard:** India Solid Waste Management (SWM) Rules, 2026 (4-Bin Segregation)

---

## 1. Executive Summary

**EcoSort-Edge 2.0** is a 100% offline, privacy-preserving edge AI system that classifies household waste in real time and generates grounded, multilingual preparation and safety steps without cloud connectivity. It pairs an ultra-compact **ExecuTorch INT8 Vision classifier (`2.41 MB`)** with a deterministic **SWM 2026 PolicyGuard** and an on-device **Llama 3.2 1B-Instruct INT8 (`1,432.96 MB`)** reasoning engine accelerated by **XNNPACK (Arm NEON / SDOT)**.

On physical **Raspberry Pi 5 (`aarch64`, 4x Cortex-A76 @ 2.4 GHz)** hardware:
- **SWM 2026 Statutory Stream Compliance:** **100.0% (30/30)** on `benchmark_30` and **100.0% (10/10)** on the stratified 10-class suite.
- **Lithium Battery / E-Waste Hazard Recall:** **100.0% (3/3)** with zero-tolerance fire-hazard interlocks.
- **Vision INT8 Throughput:** **94.88 FPS** (`10.54 ms` mean, `9.88 ms` P50, `15.34 ms` P95) across all 30 items, and **152.05 FPS** (`6.58 ms` mean) on the 10-item suite.
- **On-Device Llama 3.2 1B INT8 (`cpp_llama_main`):** **120.06 – 133.99 tok/s** prompt prefill and **6.83 – 7.33 tok/s** autoregressive decode (`3.54 – 3.77 s` pure model execution).
- **Power & Thermal Envelope:** **~4.69 W** (`0.852 V` core), **68.1 – 71.4 °C** under active cooling, and **2.65 – 2.86 GB / 8.06 GB** total system RAM used.

---

## 2. System Architecture & Dual-Platform Deployment

EcoSort-Edge 2.0 executes a deterministic 4-stage pipeline entirely on local Arm silicon:

```
Camera / Image Frame
        │
        ▼
[1] Reticle Focus Crop & Resize (224×224)
        │
        ▼
[2] ExecuTorch Vision INT8 (MobileNetV3-Small, 2.41 MB, XNNPACK)
        │  └─ Outputs: Class Probabilities, Confidence, Shannon Entropy (H)
        ▼
[3] Deterministic SWM 2026 PolicyGuard (swm2026.yaml)
        │  ├─ Fire-Hazard Interlock: battery (conf ≥ 0.30) -> Red Bin
        │  ├─ OOD / Uncertainty Gate: H > 2.00 or conf < 0.30 -> Manual Check
        │  └─ Camera-Free Sanitary Override -> Yellow Bin
        ▼
[4] Grounded Llama 3.2 1B-Instruct INT8 (1,432.96 MB, XNNPACK)
        │  └─ Generates concise, safety-grounded preparation steps
        ▼
Multilingual Output (English · தமிழ் · ಕನ್ನಡ · हिन्दी)
```

### Dual Arm Target Parity
| Component | Raspberry Pi 5 (`aarch64`) | Android Mobile (`arm64-v8a`) |
| :--- | :--- | :--- |
| **Vision Runtime** | ExecuTorch Python + XNNPACK (`waste_classifier_int8.pte`) | ExecuTorch Android AAR `Module` (`waste_classifier_int8.pte`) |
| **LLM Runtime** | Native C++ `llama_main` (`llama3_2_1b_int8.pte`) | ExecuTorch `LlmModule` (`llama3_2_1b_int8.pte`) |
| **Policy Engine** | `src/policy_guard.py` + `policy/swm2026.yaml` | `PolicyGuard.kt` + `assets/swm2026.yaml` |
| **Interfaces** | Terminal CLI (`cli_check.py`), Real-Time USB Cam (`pi5_usb_cam_check.py`), Web Console (`ui_dashboard.py`) | Jetpack Compose App (`Home`, `Scanner`, `Detail`, `Assistant` + EN/TA/KN/HI switcher) |
| **Network Dependency** | **0% (100% Air-Gapped)** | **0% (No `INTERNET` permission in Manifest)** |

---

## 3. Methodology

### 3.1 Waste Taxonomy & SWM 2026 Statutory Mapping
The vision model classifies waste into **9 fine-grained visual classes** and maps them via `policy/swm2026.yaml` into the **4 statutory SWM 2026 household bins**:
1. **Green Bin (Wet Organic):** `food_waste`, `vegetable_scrap`
2. **Blue Bin (Dry Recyclable):** `cardboard`, `paper`, `plastic_bottle`, `metal_can`, `glass_bottle`, `multi_layer_packaging`
3. **Red Bin (Special-Care / Domestic Hazardous):** `battery` (displayed in UI as **Electronic Waste**)
4. **Yellow Bin (Sanitary Waste):** `sanitary_item` (routed via dedicated camera-free privacy override)

### 3.2 Deterministic Safety & Uncertainty Guardrails
To prevent LLM hallucinations from overriding statutory safety rules:
- **Zero-Tolerance Battery Fire-Hazard Interlock:** Any detection of `battery` with confidence `≥ 0.30` is immediately intercepted (`HAZARD_INTERCEPTED`), assigned to the **Red Bin**, and tagged with mandatory isolation rules (*keep dry, tape terminals, never crush or place in Dry/Wet bins*).
- **Shannon Entropy OOD Gate:** Predictions with high uncertainty (`H > 2.00` or `confidence < 0.30`) are routed to manual verification rather than risking bin contamination.
- **Grounded Prompting:** Llama 3.2 1B INT8 receives the locked PolicyGuard decision (`item`, `assigned_bin`, `safety_flags`) rather than deciding the bin itself, ensuring 100% policy adherence.

---

## 4. Optimization Techniques

### 4.1 Vision PT2E Static INT8 Quantization + XNNPACK Delegation
- **Architecture:** Lightweight MobileNetV3-Small backbone exported with PyTorch 2 Export (`PT2E`) static INT8 quantization and lowered to **XNNPACK** (`Arm NEON / SDOT`).
- **Footprint & Speed:** Compressed to **2.41 MB**, achieving **6.58 – 10.54 ms** inference latency on Raspberry Pi 5 CPU alone without a dedicated NPU.

### 4.2 Llama 3.2 1B Quantization Ablation & Fidelity Selection
We benchmarked four quantization configurations in [llama_quantization_ablation.json](../benchmarks/llama_quantization_ablation.json) before selecting the deployment binary:

| Variant | `.pte` Size (MB) | Cosine Similarity vs BF16 | Top-1 Agreement | Selection Decision |
| :--- | :---: | :---: | :---: | :--- |
| **BF16 Baseline** | — | `1.000` | `100.0%` | Reference baseline |
| **8da4w (G=128)** | `765.3` | `0.9059` | `0.0%` | Rejected (severe accuracy/speed degradation) |
| **8da4w (G=32)** | `820.5` | `0.9208` | `33.3%` | Rejected (high output drift) |
| **INT8 (XNNPACK PT2E Dynamic)** | **`1,433.0`** | **`0.9880`** | **`100.0%`** | **Selected (near-lossless fidelity + fast C++ XNNPACK)** |

### 4.3 Edge Inference Latency & Memory Optimizations
1. **Native C++ XNNPACK Runner (`llama_main`):** Compiled ExecuTorch 1.6.0 on-device with `-DEXECUTORCH_BUILD_XNNPACK=ON` (KleidiAI + NEON/SDOT kernels enabled), yielding **120.06 – 133.99 tok/s** prefill and **6.83 – 7.33 tok/s** decode on Cortex-A76.
2. **Reticle Focus Crop:** Extracts the central subject region before resizing to `224×224`, eliminating background clutter on live camera feeds while keeping preprocessing deterministic.
3. **Bounded Generation & System Prompt Compression:** Prompts are kept concise (`82` prompt tokens) with a hard `44–72` token generation cap, delivering actionable 2-step safety guidance in **3.54 – 3.77 s** of pure model execution.
4. **In-Memory Pre-Warming & LRU Response Caching:** Both the Android app (`LlamaAdvisor` background `init`) and `LlamaExecuTorchEngine` (`OrderedDict` LRU cache, capacity `64`) eliminate repeated cold-start overhead on recurring household items.

---

## 5. Experimental Results (Measured on Physical Raspberry Pi 5)

All Raspberry Pi 5 results below were recorded directly on **Raspberry Pi 5 (`aarch64`, 4x Cortex-A76 @ `2400.0 MHz`, `8,062.3 MB` RAM)** and saved in [pi5_cli_10_report.json](../evidence/pi5_cli_10_report.json) and [pi5_cli_30_report.json](../evidence/pi5_cli_30_report.json).

### 5.1 Aggregate Benchmark Summary
| Benchmark Suite | Hardware | Samples | Class Accuracy | SWM 2026 Compliance | Hazard Recall | Mean Latency | P50 Latency | P95 Latency | Throughput |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **Pi 5 CLI 10-Class + Llama** | Raspberry Pi 5 (`aarch64`) | 10 | **80.0%** (8/10) | **100.0%** (10/10) | **100.0%** (2/2) | **6.58 ms** | **5.24 ms** | **10.52 ms** | **152.05 FPS** |
| **Pi 5 CLI Full 30-Item** | Raspberry Pi 5 (`aarch64`) | 30 | **80.0%** (24/30) | **100.0%** (30/30) | **100.0%** (3/3) | **10.54 ms** | **9.88 ms** | **15.34 ms** | **94.88 FPS** |
| **Dev Host Reference (x86)** | Windows `win32` | 30 | **80.0%** (24/30) | **100.0%** (30/30) | **100.0%** (3/3) | 9.49 ms | — | 10.71 ms | 105.37 FPS |

> **Why SWM 2026 Compliance is 100.0% despite 80.0% fine-grained class accuracy:** The 6 fine-grained confusions on `benchmark_30` occur strictly *within the same statutory bin* (e.g., `multi_layer_packaging` → `metal_can` inside **Blue Bin / Dry**, `plastic_bottle` → `cardboard` inside **Blue Bin / Dry**, and `vegetable_scrap` → `food_waste` inside **Green Bin / Wet**). Zero hazardous or wet/dry cross-contaminations occurred.

### 5.2 Per-Class Performance on Stratified 10-Image Pi 5 Run (`pi5_cli_10_report.json`)
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

### 5.3 On-Device Llama 3.2 1B INT8 Performance (`ExecuTorch C++ llama_main` on Pi 5)
Measured via `PyTorchObserver` instrumentation on `item_01_battery_01.jpg` (`Red Bin / Special-Care`):
- **Prefill Speed:** **120.06 – 133.99 tokens/sec** (`82` prompt tokens)
- **Decode Speed:** **6.83 – 7.33 tokens/sec** (`21` generated tokens)
- **Pure Model Execution Time:** **3,539 – 3,773 ms**
- **Cold Process Total Latency (incl. 1.43 GB mmap/load):** **20,764.8 ms** (batch10 run) / **14,209.1 ms** (warm page cache single-item CLI check)
- **Sample Output:**
  > *"1. Handle with care to avoid electrical shock.*  
  > *2. Secure terminals with tape to prevent short circuits."*

---

## 6. Hardware Utilization & SoC Telemetry (Raspberry Pi 5)

Live telemetry captured using `vcgencmd` and `/proc/meminfo` during active inference benchmarks:

| Telemetry Metric | 10-Item Benchmark (`pi5_cli_10_report.json`) | 30-Item Benchmark (`pi5_cli_30_report.json`) |
| :--- | :---: | :---: |
| **Architecture / Cores** | `aarch64` / 4x Arm Cortex-A76 | `aarch64` / 4x Arm Cortex-A76 |
| **Arm CPU Clock (`vcgencmd measure_clock arm`)** | `2400.0 MHz` | `2400.0 MHz` |
| **SoC Temperature (`vcgencmd measure_temp`)** | `68.1 °C` | `71.4 °C` |
| **Core Voltage (`vcgencmd measure_volts core`)** | `0.852 V` | `0.852 V` |
| **Estimated SoC Power (`5.5 × V_core`)** | **`~4.69 W`** | **`~4.69 W`** |
| **System RAM Used / Total** | `2,857.5 / 8,062.3 MB` (`35.4%`) | `2,652.7 / 8,062.3 MB` (`32.9%`) |
| **Model Storage Footprint** | Vision: `2.41 MB` · Llama: `1,432.96 MB` | Vision: `2.41 MB` · Llama: `1,432.96 MB` |

Under sustained 30-image inference and native C++ LLM execution, the Raspberry Pi 5 remained well below thermal throttling (`80–85 °C`), drawing under **5 W** and leaving over **5.2 GB** of free memory headroom on the 8 GB board.

---

## 7. Supporting Evidence Inventory (`arm_submission/evidence/`)

All experimental artifacts, hardware logs, and visual/video captures are archived in [evidence/](../evidence/):

1. **Machine-Readable Pi 5 Benchmark Reports:**
   - [pi5_cli_10_report.json](../evidence/pi5_cli_10_report.json) — 10-class stratified Vision + `llama_main` + `vcgencmd` telemetry report
   - [pi5_cli_30_report.json](../evidence/pi5_cli_30_report.json) — Full 30-image Vision + PolicyGuard + `vcgencmd` telemetry report
2. **Hardware Setup & Live Execution Photos:**
   - [WhatsApp Image 2026-11-25 at 8.53.18 PM.jpeg](../evidence/WhatsApp%20Image%202026-11-25%20at%208.53.18%20PM.jpeg) — Raspberry Pi 5 hardware rig, USB camera, `cli_check.py --batch10`, single-item Llama output, and `ui_dashboard.py` server live on `:8080`
   - [WhatsApp Image 2026-11-25 at 8.53.18 PM (1).jpeg](../evidence/WhatsApp%20Image%202026-11-25%20at%208.53.18%20PM%20(1).jpeg) — Full workstation view during Pi 5 CLI & Dashboard verification
   - [WhatsApp Image 2026-11-25 at 8.53.18 PM (2).jpeg](../evidence/WhatsApp%20Image%202026-11-25%20at%208.53.18%20PM%20(2).jpeg) — Alternate angle of Pi 5 active-cooled setup and terminal metrics
3. **Live Demo & Inference Video Captures:**
   - [WhatsApp Video 2026-11-25 at 8.52.01 PM.mp4](../evidence/WhatsApp%20Video%202026-11-25%20at%208.52.01%20PM.mp4) (`10.1 MB`)
   - [WhatsApp Video 2026-11-25 at 8.52.05 PM.mp4](../evidence/WhatsApp%20Video%202026-11-25%20at%208.52.05%20PM.mp4) (`2.17 MB`)
   - [WhatsApp Video 2026-11-25 at 8.53.17 PM.mp4](../evidence/WhatsApp%20Video%202026-11-25%20at%208.53.17%20PM.mp4) (`1.25 MB`)
   - [WhatsApp Video 2026-11-25 at 8.53.17 PM (1).mp4](../evidence/WhatsApp%20Video%202026-11-25%20at%208.53.17%20PM%20(1).mp4) (`9.28 MB`)
   - [WhatsApp Video 2026-11-25 at 8.53.17 PM (2).mp4](../evidence/WhatsApp%20Video%202026-11-25%20at%208.53.17%20PM%20(2).mp4) (`6.19 MB`)
   - [WhatsApp Video 2026-11-25 at 8.53.18 PM.mp4](../evidence/WhatsApp%20Video%202026-11-25%20at%208.53.18%20PM.mp4) (`17.95 MB`)
