# EcoSort-Edge 2.0: Air-Gapped Multimodal Waste Segregation and Hazard-Interlock Advisory on Arm Edge SoCs

**Target Platforms:** Raspberry Pi 5 (Arm Cortex-A76, `aarch64`) & Android Mobile (Armv8-A, `arm64-v8a`)  
**Compliance Standard:** India Solid Waste Management (SWM) Rules, 2026 (4-Bin Statutory Segregation)  
**Repository:** `https://github.com/Ashwinkumar-k10/ARM_SOC_ECOSORT`

---

## Abstract

Improper household waste segregation—particularly the co-mingling of lithium-ion batteries with dry recyclables—poses severe thermal-runaway and municipal recovery hazards. Cloud-dependent vision APIs introduce network latency, recurring bandwidth costs, and privacy concerns around domestic waste imagery. This paper presents **EcoSort-Edge 2.0**, a 100% air-gapped, dual-platform Arm edge AI system deployed across **Raspberry Pi 5 (`aarch64`)** and **Android (`arm64-v8a`)**. The architecture couples a **2.41 MB ExecuTorch PT2E Static INT8** vision classifier (MobileNetV3-Small lowered to Arm XNNPACK NEON/SDOT) with a deterministic **SWM 2026 PolicyGuard** and an on-device **Llama 3.2 1B-Instruct INT8 (`1,432.96 MB`)** advisory engine. On physical Raspberry Pi 5 hardware (`4× Cortex-A76 @ 2.4 GHz`), EcoSort-Edge 2.0 achieves **100.0% SWM 2026 statutory compliance (`30/30`)**, **100.0% battery fire-hazard recall (`3/3`)**, and **94.88 FPS (`10.54 ms` mean latency)**—representing a **4.02×–6.44× speedup** and **60% binary reduction** over the PyTorch FP32 baseline (`~42.40 ms`, `~6.00 MB`) while preserving **100.00% FP32 decision parity**. On-device Llama 3.2 1B INT8 (`cpp_llama_main`) reaches **120.06–133.99 tok/s** prompt prefill and **6.83–7.33 tok/s** decode within a **~4.69 W** power envelope (`68.1–71.4 °C`). On the Android mobile companion (`33.72 MB` APK, zero `INTERNET` permission), live camera hazard interception runs in **92.8 ms** with interactive **English, Tamil (`தமிழ்`), Kannada (`ಕನ್ನಡ`), and Hindi (`हिन्दी`)** localization.

**Index Terms—** Edge AI, ExecuTorch, INT8 Quantization, XNNPACK, Arm Cortex-A76, Llama 3.2 1B, SWM 2026, Waste Segregation, Android Jetpack Compose, Privacy-Preserving AI.

---

## I. Introduction

Municipal solid waste management in India under the **Solid Waste Management (SWM) Rules, 2026** mandates household-level separation into four color-coded streams: **Wet Organic (Green Bin)**, **Dry Recyclable (Blue Bin)**, **Sanitary Waste (Yellow Bin)**, and **Special-Care / Domestic Hazardous Waste (Red Bin)**. In practice, households frequently misclassify multi-layer packaging, e-waste/batteries, and soiled paper, while cloud-based AI assistants fail in low-connectivity environments and raise privacy concerns for sensitive sanitary items.

To address these constraints, **EcoSort-Edge 2.0** executes all perception, policy enforcement, and natural-language guidance locally on Arm CPU silicon without external servers:
1. **Ultra-fast INT8 Vision** (`waste_classifier_int8.pte`, `2.41 MB`) running locally on Arm NEON/SDOT via XNNPACK.
2. **Deterministic Statutory Guardrails** (`swm2026.yaml`) enforcing zero-tolerance fire-hazard interception and out-of-distribution (OOD) Shannon entropy gating.
3. **Grounded On-Device Reasoning** (`llama3_2_1b_int8.pte`, `1,432.96 MB`) generating concise, 2-step safety and preparation instructions locked to the verified bin decision.
4. **Cross-Platform Arm Parity** spanning a Raspberry Pi 5 edge appliance (CLI, real-time USB webcam, and HTTP dashboard) and an offline-first Android Jetpack Compose application.

---

## II. System Architecture & Dual-Target Arm Design

EcoSort-Edge 2.0 implements an identical 4-stage execution pipeline across Linux `aarch64` and Android `arm64-v8a`:

```
Camera / Image Input
        │
        ▼
[Stage 1] Reticle Focus Crop & Resize (224×224 / 256×256 RGB)
        │
        ▼
[Stage 2] ExecuTorch Vision INT8 (MobileNetV3-Small, 2.41 MB, XNNPACK)
        │  └─ Outputs: Softmax Probabilities, Confidence, Shannon Entropy (H)
        ▼
[Stage 3] Deterministic SWM 2026 PolicyGuard (swm2026.yaml)
        │  ├─ Fire-Hazard Interlock: battery (conf ≥ 0.30) -> Red Bin (HAZARD_INTERCEPTED)
        │  ├─ OOD / Uncertainty Gate: H > 2.00 or conf < 0.30 -> Manual Check
        │  └─ Camera-Free Sanitary Override -> Yellow Bin (SANITARY_PRIVACY_OVERRIDE)
        ▼
[Stage 4] Grounded Llama 3.2 1B-Instruct INT8 (1,432.96 MB, XNNPACK)
        │  └─ Generates concise 2-step preparation / safety guidance
        ▼
Multilingual UI & Statutory Labels (EN · தமிழ் · ಕನ್ನಡ · हिन्दी)
```

### Table I: Dual-Target Arm Implementation Matrix
| Subsystem | Raspberry Pi 5 (`aarch64`) | Android Mobile (`arm64-v8a`) |
| :--- | :--- | :--- |
| **Vision Inference** | ExecuTorch Python + XNNPACK (`waste_classifier_int8.pte`) | ExecuTorch Android AAR `Module` (`VisionClassifier.kt`) |
| **LLM Inference** | Native C++ `llama_main` (`llama3_2_1b_int8.pte`) | ExecuTorch `LlmModule` (`LlamaAdvisor.kt`) |
| **Policy Guardrails** | `src/policy_guard.py` + `policy/swm2026.yaml` | `PolicyGuard.kt` + `assets/swm2026.yaml` |
| **User Interfaces** | CLI (`cli_check.py`), Real-Time USB Cam (`pi5_usb_cam_check.py`), Web Console (`ui_dashboard.py`) | Jetpack Compose (`Home`, `Scanner`, `Detail`, `Assistant`) |
| **Telemetry** | `vcgencmd` (ARM clock, temp, core volts, est. watts) & `/proc/meminfo` | Android `ActivityManager` RAM, `BatteryManager` %/°C |
| **Network Permission** | **None (100% Air-Gapped)** | **None (`INTERNET` omitted from `AndroidManifest.xml`)** |

---

## III. Methodology & Safety Guardrails

### A. Nine-Class to Four-Bin SWM 2026 Mapping
The vision backbone classifies input crops into 9 fine-grained classes, which `PolicyGuard` maps deterministically into the 4 statutory SWM 2026 streams:
- **Green Bin (`wet`):** `food_waste`, `vegetable_scrap`
- **Blue Bin (`dry`):** `cardboard`, `paper`, `plastic_bottle`, `metal_can`, `glass_bottle`, `multi_layer_packaging`
- **Red Bin (`special_care`):** `battery` (rendered to users as **Electronic Waste**)
- **Yellow Bin (`sanitary`):** `sanitary_item` (triggered exclusively via camera-free privacy button)

### B. Deterministic Hazard & Entropy Interlocks
To guarantee that generative language modeling never compromises waste safety:
1. **Zero-Tolerance Fire-Hazard Interlock:** Any detection of `battery` with confidence `≥ 0.30` immediately triggers `HAZARD_INTERCEPTED`, locks routing to the **Red Bin**, and attaches mandatory isolation flags (`critical_fire`, `mandatory_isolation`, `epr_battery_rules_2022`).
2. **Shannon Entropy OOD Gate:** Predictions with Shannon entropy `H > 2.00` or confidence `< 0.30` are rejected to manual inspection rather than risking cross-contamination.
3. **Policy-Locked Grounded Prompting:** Llama 3.2 1B INT8 receives the locked `(predicted_class, assigned_bin, flags)` tuple from PolicyGuard and is constrained to generating concise preparation/safety steps.

---

## IV. Optimization Techniques & Ablation Studies

### A. Vision Quantization: PyTorch FP32 vs. ExecuTorch PT2E Static INT8
The MobileNetV3-Small classifier was calibrated and exported via PyTorch 2 Export (`PT2E`) static INT8 quantization (`waste_classifier_int8.pte`) and delegated to **XNNPACK** (Arm NEON / SDOT instructions).

### Table II: Vision Classifier FP32 vs. ExecuTorch INT8 Comparison
| Metric | PyTorch FP32 (`.pth`) *(Baseline)* | ExecuTorch INT8 (`.pte`) *(Laptop x86)* | ExecuTorch INT8 (`.pte`) *(Pi 5 `batch30`)* | ExecuTorch INT8 (`.pte`) *(Pi 5 `batch10`)* |
| :--- | :---: | :---: | :---: | :---: |
| **Model Binary Size** | `~6.00 MB` | **`2.41 MB`** (`-60%`) | **`2.41 MB`** (`-60%`) | **`2.41 MB`** (`-60%`) |
| **FP32 Decision Parity** | `100.00%` | **`100.00%`** (`Cosine = 0.9902`) | **`100.00%`** | **`100.00%`** |
| **Mean Inference Latency** | `~42.40 ms` | `9.49 ms` | **`10.54 ms`** (`4.02×` faster) | **`6.58 ms`** (`6.44×` faster) |
| **P50 / P95 Latency** | — | `— / 10.71 ms` | **`9.88 / 15.34 ms`** | **`5.24 / 10.52 ms`** |
| **Min / Max Latency** | — | — | `5.11 / 20.21 ms` | `4.37 / 11.44 ms` |
| **Throughput (FPS)** | `~23.6 FPS` | `105.37 FPS` | **`94.88 FPS`** | **`152.05 FPS`** |
| **Fine-Grained Class Accuracy** | `80.0%` | `80.0%` (`24/30`) | **`80.0%`** (`24/30`) | **`80.0%`** (`8/10`) |
| **SWM 2026 Compliance** | `100.0%` | `100.0%` (`30/30`) | **`100.0%`** (`30/30`) | **`100.0%`** (`10/10`) |
| **Battery Hazard Recall** | `100.0%` | `100.0%` (`3/3`) | **`100.0%`** (`3/3`) | **`100.0%`** (`2/2`) |
| **Active SoC Power** | `~6.40 W` | `N/A` (Windows) | **`~4.69 W`** | **`~4.69 W`** |

### B. LLM Quantization Ablation Study (Llama 3.2 1B-Instruct)
Four quantization configurations were evaluated in [llama_quantization_ablation.json](../benchmarks/llama_quantization_ablation.json) to balance memory footprint against reasoning fidelity:

### Table III: Llama 3.2 1B Quantization Ablation
| Quantization Variant | `.pte` Size (MB) | Cosine Similarity vs. BF16 | Top-1 Token Agreement | Host Avg TTFT (ms) | Selection Outcome |
| :--- | :---: | :---: | :---: | :---: | :--- |
| **BF16 Baseline** | Unquantized | `1.0000` | `100.0%` | `1,670.5` | Reference baseline |
| **`8da4w` (`G=128`, 4-bit weights)** | `765.3` | `0.9059` | `0.0%` | `4,058.6` | Rejected (severe logit drift & dequant overhead) |
| **`8da4w` (`G=32`, 4-bit weights)** | `820.5` | `0.9208` | `33.3%` | `4,301.7` | Rejected (high output drift) |
| **`INT8 (XNNPACK PT2E Dynamic)`** | **`1,432.96`** | **`0.9880`** | **`100.0%`** | **`1,707.1`** | **Selected (near-BF16 fidelity + fast XNNPACK)** |

### C. Edge Latency & Memory Optimizations
1. **Native C++ `llama_main` Build (ExecuTorch 1.6.0):** Compiled directly on Raspberry Pi 5 with `-DEXECUTORCH_BUILD_XNNPACK=ON` (KleidiAI + NEON/SDOT kernels), `-DEXECUTORCH_BUILD_KERNELS_OPTIMIZED=ON`, `-DEXECUTORCH_BUILD_KERNELS_QUANTIZED=ON`, and `-DEXECUTORCH_BUILD_KERNELS_LLM=ON`.
2. **Reticle Focus Crop:** Extracts the central subject region before `224×224` resizing to suppress background clutter on live camera feeds.
3. **Bounded Prompt & Decode Budget:** Uses compact `82`-token grounded prompts with a `44–72` token generation cap, yielding complete 2-step advice in **3.54–3.77 s** of pure execution.
4. **Pre-Warming & LRU Response Caching:** Android `LlamaAdvisor` pre-warms `llama3_2_1b_int8.pte` asynchronously at startup, while `LlamaExecuTorchEngine` caches up to `64` `(class, bin, question, language)` responses in an `OrderedDict` LRU cache.

---

## V. Experimental Results (Raspberry Pi 5 & Mobile Companion)

### A. Raspberry Pi 5 (`aarch64`) Quantitative Results
All Pi 5 benchmarks were executed on physical **Raspberry Pi 5 (8 GB, 4× Cortex-A76 @ `2400.0 MHz`)** and archived in [pi5_cli_10_report.json](../evidence/pi5_cli_10_report.json) and [pi5_cli_30_report.json](../evidence/pi5_cli_30_report.json).

- **Within-Stream Misclassification Analysis:** Across all 30 items (`80.0%` fine-grained accuracy, `100.0%` statutory bin compliance), the 6 fine-grained confusions occurred exclusively *inside the correct statutory bin* (`multi_layer_packaging` → `metal_can` in Blue Bin, `plastic_bottle` → `cardboard` in Blue Bin, and `vegetable_scrap` → `food_waste` in Green Bin). Zero hazardous or wet/dry cross-contaminations occurred.

### Table IV: Per-Sample Stratified Results on Pi 5 (`pi5_cli_10_report.json`)
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

### B. Llama 3.2 1B INT8 `PyTorchObserver` Profiling on Pi 5
### Table V: Native C++ `llama_main` Inference Telemetry (`item_01_battery_01.jpg`)
| Metric | `--batch10` Run (Cold Load) | Single-Item CLI Run (Warm Page Cache) |
| :--- | :---: | :---: |
| **Prompt Prefill Speed (`prefill_token_per_sec`)** | **120.06 tok/s** (`82` prompt tokens) | **133.99 tok/s** |
| **Token Decode Speed (`decode_token_per_sec`)** | **7.33 tok/s** (`21` generated tokens) | **6.83 tok/s** |
| **Time to First Token (TTFT)** | **683 ms** (`0.68 s`) | — |
| **Pure Model Execution (`aggregate_model_execution_time_ms`)** | **3,539 ms** (`3.54 s`) | **3,773 ms** (`3.77 s`) |
| **Sampling Overhead (`aggregate_sampling_time_ms`)** | **3 ms** | **3 ms** |
| **Model Load Time (`1,432.96 MB` `.pte`)** | **17,061 ms** (`17.06 s`) | `~10,436 ms` |
| **Total Subprocess Wall Time** | **20,764.8 ms** | **14,209.1 ms** |
| **Generated Safety Advice** | *"1. Handle with care to avoid electrical shock. 2. Secure terminals with tape to prevent short circuits."* | Same safety-grounded steps |

### C. Raspberry Pi 5 Hardware Utilization & Thermal Telemetry
### Table VI: `vcgencmd` & `/proc/meminfo` Hardware Telemetry
| Telemetry Parameter | 10-Item Benchmark + Llama | 30-Item Benchmark |
| :--- | :---: | :---: |
| **Architecture / CPU Cores** | `aarch64` / 4× Arm Cortex-A76 | `aarch64` / 4× Arm Cortex-A76 |
| **ARM Core Clock (`measure_clock arm`)** | `2400.0 MHz` | `2400.0 MHz` |
| **SoC Temperature (`measure_temp`)** | `68.1 °C` | `71.4 °C` |
| **Core Voltage (`measure_volts core`)** | `0.852 V` | `0.852 V` |
| **Estimated SoC Power (`5.5 × V_core`)** | **`~4.69 W`** | **`~4.69 W`** |
| **RAM Used / Total** | `2,857.5 / 8,062.3 MB` (`35.4%`) | `2,652.7 / 8,062.3 MB` (`32.9%`) |

### D. Android Mobile Application Results & UX Verification
The companion Android application (`app-debug.apk`, **`33,719,278` bytes / `32.16 MB`**, `compileSdk = 35`, built with Gradle 8.7) was deployed and verified on physical **Armv8-A (`arm64-v8a`)** smartphone hardware:

### Table VII: Android Mobile Companion Verification Summary
| Mobile Evaluation Dimension | Verified Observation |
| :--- | :--- |
| **Live Hazard Detection Latency** | **`92.8 ms`** end-to-end camera capture + reticle crop + ExecuTorch Vision INT8 + PolicyGuard |
| **Live Hazard Interception Output** | Correctly intercepted hazardous capture: **`43% confidence`**, **`H = 1.29`**, Status **`HAZARD INTERCEPTED`**, routed to **Red Bin (Special-Care / Domestic Hazardous)** |
| **Safety & UX Enhancements** | Red Bin class rendered as **`Electronic Waste`**; fire-hazard warning banner; custom padded launcher & in-app EcoSort branding; high-contrast white typography on dark forest-green actions |
| **On-Device LLM Integration** | `LlamaAdvisor.kt` (`ExecuTorch LlmModule`) auto-triggers safety steps upon capture with one-time `llama3_2_1b_int8.pte` (`1.43 GB`) SAF import and background pre-warming |
| **Multilingual Accessibility** | Instant persisted 4-language switcher (**English**, **தமிழ்**, **ಕನ್ನಡ**, **हिन्दी**) translating UI controls, bin names, object classes, statutory SWM 2026 labels, and Llama prompt instructions |
| **Privacy & Offline Guarantee** | Zero `INTERNET` permission; dedicated **Sanitary Path** camera-free override routes directly to **Yellow Bin** without capturing an image |

---

## VI. Conclusion

**EcoSort-Edge 2.0** demonstrates that real-time waste classification (`6.58–10.54 ms`, `94.88–152.05 FPS` on Pi 5; `92.8 ms` on mobile camera capture), zero-tolerance safety compliance (`100.0%` SWM 2026 compliance and `100.0%` battery hazard recall), and grounded LLM advisory (`120.06–133.99 tok/s` prefill, `6.83–7.33 tok/s` decode) can run **100% offline** on commodity Arm edge processors under a **~4.69 W** power budget.

---

## VII. Submission Artifacts & Supporting Evidence (`arm_submission/`)

- **Android APK:** `android_app/app/build/outputs/apk/debug/app-debug.apk` (`33,719,278` bytes)
- **Machine-Readable Pi 5 Reports:**
  - [pi5_cli_10_report.json](../evidence/pi5_cli_10_report.json)
  - [pi5_cli_30_report.json](../evidence/pi5_cli_30_report.json)
  - [llama_quantization_ablation.json](../benchmarks/llama_quantization_ablation.json)
  - [vision_benchmark_30.json](../benchmarks/vision_benchmark_30.json)
- **Hardware & Terminal Captures (`evidence/`):**
  - [WhatsApp Image 2026-11-25 at 8.53.18 PM.jpeg](../evidence/WhatsApp%20Image%202026-11-25%20at%208.53.18%20PM.jpeg)
  - [WhatsApp Image 2026-11-25 at 8.53.18 PM (1).jpeg](../evidence/WhatsApp%20Image%202026-11-25%20at%208.53.18%20PM%20(1).jpeg)
  - [WhatsApp Image 2026-11-25 at 8.53.18 PM (2).jpeg](../evidence/WhatsApp%20Image%202026-11-25%20at%208.53.18%20PM%20(2).jpeg)
- **Live Demo & Inference Video Captures (`evidence/`):**
  - [WhatsApp Video 2026-11-25 at 8.52.01 PM.mp4](../evidence/WhatsApp%20Video%202026-11-25%20at%208.52.01%20PM.mp4) (`10.1 MB`)
  - [WhatsApp Video 2026-11-25 at 8.52.05 PM.mp4](../evidence/WhatsApp%20Video%202026-11-25%20at%208.52.05%20PM.mp4) (`2.17 MB`)
  - [WhatsApp Video 2026-11-25 at 8.53.17 PM.mp4](../evidence/WhatsApp%20Video%202026-11-25%20at%208.53.17%20PM.mp4) (`1.25 MB`)
  - [WhatsApp Video 2026-11-25 at 8.53.17 PM (1).mp4](../evidence/WhatsApp%20Video%202026-11-25%20at%208.53.17%20PM%20(1).mp4) (`9.28 MB`)
  - [WhatsApp Video 2026-11-25 at 8.53.17 PM (2).mp4](../evidence/WhatsApp%20Video%202026-11-25%20at%208.53.17%20PM%20(2).mp4) (`6.19 MB`)
  - [WhatsApp Video 2026-11-25 at 8.53.18 PM.mp4](../evidence/WhatsApp%20Video%202026-11-25%20at%208.53.18%20PM.mp4) (`17.95 MB`)
