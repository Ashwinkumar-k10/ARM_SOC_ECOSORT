"""
arm_submission/src/multimodal_orchestrator.py
Coordinates ExecuTorch Vision -> Deterministic Policy Guard -> ExecuTorch Llama.
Enforces Decision-Path Isolation: Llama provides reasoning/education grounded
in the DPG statutory decision and cannot override bin routing.
"""

from pathlib import Path
from typing import Dict, Optional
import numpy as np

from policy_guard import PolicyGuard
from vision_engine import VisionEngine
from llama_engine import LlamaExecuTorchEngine


class MultimodalOrchestrator:
    def __init__(
        self,
        vision_model_path: Optional[Path] = None,
        llama_model_path: Optional[Path] = None,
        tokenizer_path: Optional[Path] = None,
        policy_path: Optional[Path] = None,
    ):
        self.vision = VisionEngine(vision_model_path)
        self.policy = PolicyGuard(policy_path)
        self.llama = LlamaExecuTorchEngine(llama_model_path, tokenizer_path)

    def process_frame(
        self,
        frame_bgr: np.ndarray,
        sanitary_button: bool = False,
        user_question: Optional[str] = None,
        language: str = "en",
        run_llama: bool = True,
    ) -> Dict:
        """Run complete perception + statutory guard + optional Llama advisory."""
        if sanitary_button:
            decision = self.policy.evaluate("sanitary_item", 1.0, sanitary_button=True)
            vision_meta = {"inference_ms": 0.0, "pipeline_ms": 0.0, "bbox": [0, 0, 0, 0]}
        else:
            vision_meta = self.vision.classify_frame(frame_bgr)
            decision = self.policy.evaluate(
                predicted_class=vision_meta["predicted_class"],
                confidence=vision_meta["confidence"],
                entropy=vision_meta["entropy"],
            )

        result = {
            "vision": vision_meta,
            "decision": decision,
            "advisory": None,
        }

        if run_llama:
            result["advisory"] = self.llama.generate(
                decision=decision,
                user_question=user_question,
                language=language,
            )

        return result
