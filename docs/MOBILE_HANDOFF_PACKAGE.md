# EcoSort-Edge 2.0 — Mobile App On-Phone Inference Handoff Package

**Target:** Bharat AI-SoC Challenge 2026–27 (PS2) · **Mode:** 100% Offline On-Phone ExecuTorch Inference

---

## 1. Exact Files to Copy into Android App (`app/src/main/assets/`)

1. **`arm_submission/models/waste_classifier_int8.pte`** (2.41 MB) — ExecuTorch INT8 Vision model (already ready).
2. **`arm_submission/policy/swm2026.yaml`** — Statutory SWM 2026 bin names, `color_hex` codes, hazard flags, and multilingual labels.
3. **`arm_submission/models/llama3_2_1b_int8.pte`** (1,433.0 MB) + **`tokenizer.model`** — ExecuTorch Llama 3.2 1B-Instruct (`--pt2e_quantize xnnpack_dynamic`, INT8) for on-phone advisory & Q&A.
4. **Test Images:** `PS3/Img1.png` – `PS3/Img10.png` for offline verification.

*(Reference Python logic to mirror in Kotlin: `arm_submission/src/policy_guard.py` and `arm_submission/src/llama_engine.py`.)*

---

## 2. Vision Model Contract (`waste_classifier_int8.pte`)

- **Input Tensor:** `[1, 3, 256, 256]` (`NCHW`, `float32`, RGB)
- **Normalization:** `(pixel / 255.0 - mean) / std`
  - `mean = [0.485, 0.456, 0.406]`
  - `std  = [0.229, 0.224, 0.225]`
- **Output Tensor:** `[1, 9]` logits → apply `softmax`.
- **Class Index Order (`0..8`):**
  `0: battery`, `1: cardboard`, `2: food_waste`, `3: glass_bottle`, `4: metal_can`, `5: multi_layer_packaging`, `6: paper`, `7: plastic_bottle`, `8: vegetable_scrap`

---

## 3. Deterministic Policy Guard (Must Match `policy_guard.py`)

1. **Sanitary Privacy Override:** If sanitary button tapped → `stream = "sanitary"` (Yellow Bin `#F9A825`), no camera save.
2. **Battery Fire-Hazard Interlock:** If `predicted_class == "battery"` and `confidence >= 0.30` → `stream = "special_care"` (Red Bin `#C62828`), trigger hazard warning.
3. **Shannon Entropy OOD Gate:** $H = -\sum_{i=0}^{8} p_i \ln(p_i + 10^{-12})$. If $H > 2.00$ or `confidence < 0.30` → `stream = "reject"` (Grey Bin `#616161`).
4. **Otherwise:** Map `predicted_class` via `swm2026.yaml` → `classes[predicted_class].stream`.

---

## 4. On-Phone Llama 3.2 Prompt Contract (`llama3_2_1b_q4.pte`)

Always inject the **already-decided** DPG output into Llama so it never hallucinates bin routing:

```text
<|begin_of_text|><|start_header_id|>system<|end_header_id|>

You are EcoSort-Edge, an offline waste-segregation & recycling advisor. Follow the mandatory bin assignment strictly and never contradict it. Respond clearly in language='{language}' in under 90 words.<|eot_id|><|start_header_id|>user<|end_header_id|>

Detected item: {predicted_class} ({conf_pct}% confidence)
Mandatory SWM 2026 Bin: {bin_name}
Hazard level: {hazard_level} | Handling flags: {flags}
Question: {user_question}<|eot_id|><|start_header_id|>assistant<|end_header_id|>
```
