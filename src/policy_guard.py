"""
arm_submission/src/policy_guard.py
Deterministic Policy Guard (DPG) for EcoSort-Edge 2.0 (SWM Rules 2026).
Enforces statutory 4-stream bin routing, zero-tolerance battery fire-hazard
interlock, and Shannon entropy ambiguity gating.
"""

from pathlib import Path
from typing import Any, Dict, Optional
import yaml

DEFAULT_POLICY_PATH = Path(__file__).resolve().parent.parent / "policy" / "swm2026.yaml"


class PolicyGuard:
    """Stateless statutory policy layer isolating bin actuation from LLM output."""

    def __init__(self, policy_path: Optional[Path] = None):
        self.policy_path = Path(policy_path) if policy_path else DEFAULT_POLICY_PATH
        if not self.policy_path.exists():
            raise FileNotFoundError(f"SWM 2026 policy file not found: {self.policy_path}")
        with open(self.policy_path, "r", encoding="utf-8") as f:
            self.config = yaml.safe_load(f)
        self.streams = self.config["streams"]
        self.classes = self.config["classes"]

    def evaluate(
        self,
        predicted_class: str,
        confidence: float,
        entropy: Optional[float] = None,
        sanitary_button: bool = False,
        entropy_threshold: float = 2.00,
        benign_threshold: float = 0.30,
        battery_interlock_threshold: float = 0.30,
    ) -> Dict[str, Any]:
        """Map raw vision probabilities into a statutory SWM 2026 bin decision."""
        if sanitary_button:
            s = self.streams["sanitary"]
            c = self.classes.get("sanitary_item", {})
            return {
                "stream": "sanitary",
                "stream_id": s["id"],
                "bin_name": s["bin_name"],
                "color_hex": s["color_hex"],
                "status": "SANITARY_PRIVACY_OVERRIDE",
                "hazard_level": "biohazard",
                "action": s["action"],
                "flags": c.get("flags", []),
                "labels": s["labels"],
                "predicted_class": "sanitary_item",
                "confidence": 1.0,
                "entropy": 0.0,
            }

        # Zero-tolerance fire-hazard interlock for batteries
        if predicted_class == "battery" and confidence >= battery_interlock_threshold:
            s = self.streams["special_care"]
            c = self.classes["battery"]
            return {
                "stream": "special_care",
                "stream_id": s["id"],
                "bin_name": s["bin_name"],
                "color_hex": s["color_hex"],
                "status": "HAZARD_INTERCEPTED",
                "hazard_level": c.get("hazard_level", "critical_fire"),
                "action": s["action"],
                "flags": c.get("flags", []),
                "labels": s["labels"],
                "predicted_class": predicted_class,
                "confidence": round(confidence, 4),
                "entropy": round(entropy, 4) if entropy is not None else None,
            }

        # Out-of-distribution / ambiguity gate
        if (entropy is not None and entropy > entropy_threshold) or confidence < benign_threshold:
            s = self.streams["reject"]
            return {
                "stream": "reject",
                "stream_id": s["id"],
                "bin_name": s["bin_name"],
                "color_hex": s["color_hex"],
                "status": "AMBIGUITY_HOLD",
                "hazard_level": "unknown",
                "action": s["action"],
                "flags": ["manual_inspection_required"],
                "labels": s["labels"],
                "predicted_class": predicted_class,
                "confidence": round(confidence, 4),
                "entropy": round(entropy, 4) if entropy is not None else None,
            }

        c = self.classes.get(predicted_class)
        if not c:
            s = self.streams["reject"]
            return {
                "stream": "reject",
                "stream_id": s["id"],
                "bin_name": s["bin_name"],
                "color_hex": s["color_hex"],
                "status": "UNKNOWN_CLASS",
                "hazard_level": "unknown",
                "action": s["action"],
                "flags": ["unmapped_class"],
                "labels": s["labels"],
                "predicted_class": predicted_class,
                "confidence": round(confidence, 4),
                "entropy": round(entropy, 4) if entropy is not None else None,
            }

        stream_key = c["stream"]
        s = self.streams[stream_key]
        return {
            "stream": stream_key,
            "stream_id": s["id"],
            "bin_name": s["bin_name"],
            "color_hex": s["color_hex"],
            "status": "STATUTORY_ROUTED",
            "hazard_level": c.get("hazard_level", "none"),
            "action": s["action"],
            "flags": c.get("flags", []),
            "labels": s["labels"],
            "predicted_class": predicted_class,
            "confidence": round(confidence, 4),
            "entropy": round(entropy, 4) if entropy is not None else None,
        }
