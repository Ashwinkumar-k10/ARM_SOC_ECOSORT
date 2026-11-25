# EcoSort Commercial Ecosystem — UI Module

> **Modular, segregated user interface prototypes for the EcoSort On-Device Waste Segregation Ecosystem.**

This folder encapsulates all web UI prototypes, design token specifications, screen mockups, and the interactive device showcase.

---

## Directory Layout

```text
ui/
├── index.html                  # Interactive device showcase portal
├── DESIGN.md                   # Full design token specs (typography, colors, rhythms)
├── README.md                   # This UI documentation
├── screenshots/                # High-resolution screen captures
│   ├── home.png
│   ├── camera-scanner.png
│   ├── item-details.png
│   └── eco-assistant.png
└── screens/                    # Segregated application screens
    ├── home/
    │   └── index.html          # Overview dashboard, impact stats & scan CTA
    ├── camera-scanner/
    │   └── index.html          # Camera feed with reticle & classification states
    ├── item-details/
    │   └── index.html          # Material breakdown, bin allocation & disposal log
    └── eco-assistant/
        └── index.html          # Local on-device conversational environmental guide
```

---

## Running the Showcase

Open `index.html` in any browser or launch via static server:

```bash
# From repository root:
python -m http.server 8000
# Then visit: http://localhost:8000/ui/
```

Or from within `ui/`:
```bash
cd ui
python -m http.server 8000
# Visit: http://localhost:8000
```

---

## Screen Inventory

1. **Home Dashboard (`screens/home/index.html`)**
   - Personal impact metrics and on-device AI status indicator.
   - Quick action to launch camera scanning.
   - Recent sorting activity log with click-through to item details.

2. **Live Camera Scanner (`screens/camera-scanner/index.html`)**
   - Full viewport camera simulation with minimalist corner reticle.
   - Interactive state switcher: `Ready`, `Identified`, and `Uncertain`.
   - Mechanical shutter action and flashlight toggle.

3. **Classification Details (`screens/item-details/index.html`)**
   - High-confidence identification percentage and material composition.
   - Statutory SWM 2026 designated bin color hopper (Blue / Green / Red / Black).
   - Contamination warnings and preparation tips.

4. **Eco Assistant (`screens/eco-assistant/index.html`)**
   - 100% offline conversational interface.
   - Context-aware pill displaying the currently scanned item.
   - Suggested prompts and local environmental intelligence Q&A.
