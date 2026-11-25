"""
arm_submission/src/ui_dashboard.py
Standalone Web UI Launcher for EcoSort-Edge 2.0.
Launches the compact single-screen evaluation & Llama 3.2 1B INT8 dashboard
on http://localhost:8080 (auto-opens in browser unless --no-browser is set).

Usage:
  python src/ui_dashboard.py
  python src/ui_dashboard.py --port 8080 --no-browser
"""

import argparse
import sys
import threading
import webbrowser
from http.server import HTTPServer
from pathlib import Path

SRC_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(SRC_DIR))

import pi5_edge_server as srv


def main():
    parser = argparse.ArgumentParser(description="EcoSort-Edge 2.0 Web UI Dashboard")
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=8080)
    parser.add_argument("--no-browser", action="store_true", help="Do not auto-open browser")
    args = parser.parse_args()

    print("[*] Starting EcoSort-Edge 2.0 Web UI (Vision INT8 + SWM 2026 + Llama 3.2 1B INT8)...")
    srv.STATE = srv.EdgeAppState()
    server = HTTPServer((args.host, args.port), srv.EdgeAPIHandler)
    url = f"http://localhost:{args.port}"
    print(f"[✓] UI Dashboard Live: {url}")

    if not args.no_browser:
        threading.Timer(0.8, lambda: webbrowser.open(url)).start()

    server.serve_forever()


if __name__ == "__main__":
    main()
