"""
arm_submission/models/download_llama_model.py
Downloads the pre-exported Llama 3.2 1B-Instruct INT8 ExecuTorch binary
(llama3_2_1b_int8.pte, 1,433.0 MB) from Google Drive and verifies SHA-256.
"""

import hashlib
import subprocess
import sys
from pathlib import Path

FILE_ID = "1nsRVUEiAzADBwbuycSLUVyXbH8gPX31F"
DRIVE_URL = f"https://drive.google.com/file/d/{FILE_ID}/view?usp=sharing"
EXPECTED_SHA256 = "f08c9e7b1c71e45a1a637c72a3fb2705fc74caddfad82261e6f57189219344a5"
OUT_PATH = Path(__file__).resolve().parent / "llama3_2_1b_int8.pte"


def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def main():
    if OUT_PATH.exists():
        digest = sha256_file(OUT_PATH)
        if digest.lower() == EXPECTED_SHA256:
            print(f"[✓] {OUT_PATH.name} already present and SHA-256 verified.")
            return
        print(f"[!] Existing file hash mismatch ({digest}); re-downloading...")

    try:
        import gdown
    except ImportError:
        print("[*] Installing gdown for large Google Drive download...")
        subprocess.run([sys.executable, "-m", "pip", "install", "-q", "gdown"], check=True)
        import gdown

    print(f"[*] Downloading llama3_2_1b_int8.pte (1.40 GB) from Google Drive...")
    gdown.download(id=FILE_ID, output=str(OUT_PATH), quiet=False)

    if not OUT_PATH.exists():
        raise RuntimeError(f"Download failed. Download manually from: {DRIVE_URL}")

    digest = sha256_file(OUT_PATH)
    if digest.lower() != EXPECTED_SHA256:
        raise ValueError(f"SHA-256 mismatch! Expected {EXPECTED_SHA256}, got {digest}")

    print(f"[✓] Downloaded & verified: {OUT_PATH} ({OUT_PATH.stat().st_size / (1024**2):.1f} MB)")


if __name__ == "__main__":
    main()
