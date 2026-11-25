"""
arm_submission/src/pi5_edge_server.py
100% Offline EcoSort-Edge 2.0 Dashboard & Edge API Server (INT8 Only, No Fallbacks).
Runs ExecuTorch INT8 Vision (waste_classifier_int8.pte) + PolicyGuard +
Llama 3.2 1B INT8 (C++ llama_main on Pi 5, TorchAO INT8 on dev host).
"""

import argparse
import json
import subprocess
import sys
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path
from urllib.parse import unquote
import cv2
import numpy as np

SRC_DIR = Path(__file__).resolve().parent
SUBMISSION_ROOT = SRC_DIR.parent
PROJECT_ROOT = SUBMISSION_ROOT.parent
BENCHMARK_30_DIR = (
    SUBMISSION_ROOT / "benchmark_30"
    if (SUBMISSION_ROOT / "benchmark_30").exists()
    else PROJECT_ROOT / "benchmark_30"
)
sys.path.insert(0, str(SRC_DIR))

from policy_guard import PolicyGuard
from vision_engine import VisionEngine
from llama_engine import LlamaExecuTorchEngine

DASHBOARD_HTML = """<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>EcoSort-Edge 2.0 | ExecuTorch INT8 Console</title>
  <style>
    :root { --bg:#090d16; --panel:#111827; --card:#1a2234; --border:#243047; --text:#f8fafc; --muted:#94a3b8; --accent:#38bdf8; --blue:#2563eb; }
    * { box-sizing:border-box; margin:0; padding:0; font-family:Inter, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; }
    body { background:var(--bg); color:var(--text); height:100vh; overflow:hidden; display:flex; flex-direction:column; font-size:13px; }
    header { height:50px; background:var(--panel); border-bottom:1px solid var(--border); padding:0 18px; display:flex; justify-content:space-between; align-items:center; flex-shrink:0; }
    .brand { display:flex; align-items:center; gap:10px; }
    .brand h1 { font-size:15px; font-weight:700; }
    .brand span { font-size:11px; color:var(--muted); }
    .actions { display:flex; align-items:center; gap:8px; }
    .pill { background:var(--card); border:1px solid var(--border); padding:4px 10px; border-radius:999px; font-size:11px; font-weight:600; color:var(--muted); }
    .pill.ok { border-color:#10b981; color:#34d399; background:rgba(16,185,129,0.08); }
    .btn { background:var(--blue); color:#fff; border:none; border-radius:6px; padding:6px 12px; font-size:12px; font-weight:600; cursor:pointer; }
    .btn:hover { opacity:0.9; }
    .btn.ghost { background:var(--card); border:1px solid var(--border); color:var(--text); }
    .btn.red { background:#dc2626; }

    .workspace { display:grid; grid-template-columns:250px 1fr; flex:1; min-height:0; }
    .sidebar { background:var(--panel); border-right:1px solid var(--border); display:flex; flex-direction:column; min-height:0; }
    .side-hdr { padding:10px 12px; border-bottom:1px solid var(--border); display:flex; justify-content:space-between; align-items:center; }
    .side-hdr span { font-size:11px; font-weight:700; text-transform:uppercase; color:var(--muted); letter-spacing:0.05em; }
    .nav-btns { display:flex; gap:4px; }
    .nav-btns button { background:var(--card); border:1px solid var(--border); color:var(--text); border-radius:4px; padding:2px 8px; font-size:11px; cursor:pointer; }
    .item-list { flex:1; overflow-y:auto; padding:6px; display:flex; flex-direction:column; gap:3px; }
    .item-row { display:flex; align-items:center; gap:8px; padding:7px 10px; border-radius:6px; cursor:pointer; font-size:12px; color:var(--muted); border:1px solid transparent; }
    .item-row:hover { background:var(--card); color:var(--text); }
    .item-row.active { background:var(--card); border-color:var(--accent); color:var(--text); font-weight:600; }
    .dot { width:8px; height:8px; border-radius:50%; flex-shrink:0; }

    .main { padding:14px 18px; display:grid; grid-template-rows:230px 1fr; gap:14px; min-height:0; }
    .top-row { display:grid; grid-template-columns:300px 1fr; gap:14px; min-height:0; }
    .stage { background:#05080f; border:1px solid var(--border); border-radius:10px; display:flex; align-items:center; justify-content:center; overflow:hidden; position:relative; }
    .stage canvas { max-width:100%; max-height:100%; object-fit:contain; }
    .stage-tag { position:absolute; top:8px; left:8px; background:rgba(9,13,22,0.8); border:1px solid var(--border); padding:3px 8px; border-radius:4px; font-size:11px; color:var(--text); }

    .decision-box { background:var(--panel); border:1px solid var(--border); border-radius:10px; padding:12px 16px; display:flex; flex-direction:column; justify-content:space-between; }
    .bin-banner { border-radius:8px; padding:10px 14px; display:flex; justify-content:space-between; align-items:center; }
    .bin-banner .name { font-size:16px; font-weight:700; color:#fff; }
    .bin-banner .badge { background:rgba(0,0,0,0.3); padding:4px 8px; border-radius:4px; font-size:10px; font-weight:700; color:#fff; }
    .kpis { display:grid; grid-template-columns:repeat(4, 1fr); gap:10px; }
    .kpi { background:var(--card); border:1px solid var(--border); border-radius:8px; padding:8px 10px; }
    .kpi .v { font-size:15px; font-weight:700; color:var(--accent); white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
    .kpi .l { font-size:10px; color:var(--muted); margin-top:2px; }
    .lang-strip { font-size:11px; color:var(--muted); white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }

    .bottom-panel { background:var(--panel); border:1px solid var(--border); border-radius:10px; padding:12px 16px; display:flex; flex-direction:column; gap:10px; min-height:0; }
    .tab-hdr { display:flex; justify-content:space-between; align-items:center; border-bottom:1px solid var(--border); padding-bottom:8px; }
    .tabs { display:flex; gap:6px; }
    .tab-btn { background:transparent; border:1px solid transparent; color:var(--muted); padding:5px 12px; border-radius:6px; font-size:12px; font-weight:600; cursor:pointer; }
    .tab-btn.active { background:var(--card); border-color:var(--border); color:var(--text); }
    .pane { display:none; flex-direction:column; gap:10px; flex:1; min-height:0; }
    .pane.active { display:flex; }

    .llama-out { background:var(--card); border:1px solid var(--border); border-radius:8px; padding:12px 14px; flex:1; overflow-y:auto; font-size:13px; line-height:1.55; color:#e2e8f0; white-space:pre-wrap; }
    .chat-row { display:flex; gap:8px; align-items:center; }
    .chat-row input[type="text"] { flex:1; background:var(--card); border:1px solid var(--border); border-radius:6px; padding:8px 12px; color:var(--text); font-size:12px; outline:none; }
    .chat-row input[type="text"]:focus { border-color:var(--accent); }
    .chk { display:flex; align-items:center; gap:5px; font-size:11px; color:var(--muted); cursor:pointer; }

    .tbl-wrap { flex:1; overflow-y:auto; border:1px solid var(--border); border-radius:8px; }
    table { width:100%; border-collapse:collapse; font-size:12px; }
    th, td { padding:6px 10px; text-align:left; border-bottom:1px solid var(--border); }
    th { background:var(--card); color:var(--muted); position:sticky; top:0; }
    .ok-t { color:#34d399; font-weight:700; }
    .err-t { color:#f87171; font-weight:700; }
  </style>
</head>
<body>
  <header>
    <div class="brand">
      <h1>EcoSort-Edge 2.0</h1>
      <span>Arm ExecuTorch INT8 · Vision + Llama 3.2 1B · SWM 2026</span>
    </div>
    <div class="actions">
      <span class="pill ok" id="pillVision">Vision INT8</span>
      <span class="pill ok" id="pillLlama">Llama INT8</span>
      <input type="file" id="fileInput" accept="image/*" style="display:none" onchange="uploadCustom(event)" />
      <button class="btn ghost" onclick="document.getElementById('fileInput').click()">Upload</button>
      <button class="btn" onclick="runBatch30()">Run All 30</button>
      <button class="btn red" onclick="triggerSanitary()">Sanitary</button>
    </div>
  </header>

  <div class="workspace">
    <aside class="sidebar">
      <div class="side-hdr">
        <span id="sideCount">Benchmark 30</span>
        <div class="nav-btns">
          <button onclick="stepSample(-1)">Prev</button>
          <button onclick="stepSample(1)">Next</button>
        </div>
      </div>
      <div class="item-list" id="itemList"></div>
    </aside>

    <main class="main">
      <div class="top-row">
        <div class="stage">
          <span class="stage-tag" id="gtBadge">GT: —</span>
          <canvas id="viewCanvas"></canvas>
        </div>
        <div class="decision-box">
          <div class="bin-banner" id="binBanner" style="background:#1e293b;">
            <span class="name" id="binName">Select an item</span>
            <span class="badge" id="binStatus">IDLE</span>
          </div>
          <div class="kpis">
            <div class="kpi"><div class="v" id="kClass">—</div><div class="l">Predicted Class</div></div>
            <div class="kpi"><div class="v" id="kConf">—</div><div class="l">Confidence</div></div>
            <div class="kpi"><div class="v" id="kLat">—</div><div class="l">Vision Latency</div></div>
            <div class="kpi"><div class="v" id="kEnt">—</div><div class="l">Entropy (H)</div></div>
          </div>
          <div class="lang-strip" id="langStrip">SWM 2026 Labels: —</div>
        </div>
      </div>

      <section class="bottom-panel">
        <div class="tab-hdr">
          <div class="tabs">
            <button class="tab-btn active" onclick="switchTab('paneLlama', this)">Llama 3.2 1B Advisor</button>
            <button class="tab-btn" onclick="switchTab('paneBatch', this)">Batch 30 Report</button>
          </div>
          <div style="display:flex; align-items:center; gap:12px;">
            <label class="chk"><input type="checkbox" id="autoLlama" checked /> Auto-Advise</label>
            <span id="llamaTiming" style="font-size:11px; color:var(--muted);">Ready</span>
          </div>
        </div>

        <div class="pane active" id="paneLlama">
          <div class="llama-out" id="llamaBox">Select an image or click "Advise" to generate on-device Llama 3.2 1B disposal & recycling guidance.</div>
          <div class="chat-row">
            <input type="text" id="chatInput" placeholder="Ask Llama a question about this item..." onkeydown="if(event.key==='Enter') askLlama()" />
            <button class="btn ghost" onclick="askLlama(true)">Advise</button>
            <button class="btn" onclick="askLlama(false)">Send</button>
          </div>
        </div>

        <div class="pane" id="paneBatch">
          <div id="batchSummary" style="font-size:12px; color:#34d399; font-weight:600;">Click "Run All 30" in the top bar to evaluate all 30 images.</div>
          <div class="tbl-wrap">
            <table>
              <thead><tr><th>ID</th><th>True</th><th>Predicted</th><th>Conf</th><th>SWM Stream</th><th>ms</th></tr></thead>
              <tbody id="batchBody"></tbody>
            </table>
          </div>
        </div>
      </section>
    </main>
  </div>

  <script>
    const STREAM_DOT = { wet:'#22c55e', dry:'#3b82f6', special_care:'#ef4444', sanitary:'#eab308', reject:'#64748b' };
    let items = [];
    let currentIdx = 0;
    let currentDecision = null;
    let reqToken = 0;

    function switchTab(id, btn) {
      document.querySelectorAll('.pane').forEach(p => p.classList.remove('active'));
      document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
      document.getElementById(id).classList.add('active');
      btn.classList.add('active');
    }

    async function init() {
      const st = await (await fetch('/api/status')).json();
      document.getElementById('pillVision').textContent = `Vision INT8: ${st.vision_load_ms}ms`;
      document.getElementById('pillLlama').textContent = st.llama_ready ? `Llama: ${st.llama_backend}` : 'Llama: Off';

      const b30 = await (await fetch('/api/benchmark30')).json();
      items = b30.items || [];
      document.getElementById('sideCount').textContent = `Benchmark 30 (${items.length})`;
      document.getElementById('itemList').innerHTML = items.map((it, i) => `
        <div class="item-row" id="row-${i}" onclick="selectSample(${i})">
          <span class="dot" style="background:${STREAM_DOT[it.statutory_stream] || '#64748b'}"></span>
          <span>${it.sample_id.replace('item_','')} · ${it.true_class}</span>
        </div>
      `).join('');
      if (items.length > 0) selectSample(0);
    }

    function stepSample(delta) {
      if (!items.length) return;
      selectSample((currentIdx + delta + items.length) % items.length);
    }

    function drawStage(imgSrc, bbox, colorHex) {
      const canvas = document.getElementById('viewCanvas');
      const ctx = canvas.getContext('2d');
      if (!imgSrc) {
        canvas.width = 360; canvas.height = 200;
        ctx.fillStyle = '#0f172a'; ctx.fillRect(0, 0, 360, 200);
        ctx.fillStyle = '#fbbf24'; ctx.font = 'bold 14px sans-serif';
        ctx.fillText('PRIVACY MODE ACTIVE', 95, 105);
        return;
      }
      const img = new Image();
      img.onload = () => {
        canvas.width = img.width; canvas.height = img.height;
        ctx.drawImage(img, 0, 0);
        if (bbox && bbox.length === 4) {
          ctx.strokeStyle = colorHex || '#38bdf8';
          ctx.lineWidth = Math.max(4, img.width / 130);
          ctx.strokeRect(bbox[0], bbox[1], bbox[2], bbox[3]);
        }
      };
      img.src = imgSrc;
    }

    function renderVision(res, imgSrc, gt) {
      const vis = res.vision;
      const dec = res.decision;
      currentDecision = dec;
      drawStage(imgSrc, vis ? vis.bbox : null, dec.color_hex);

      document.getElementById('binBanner').style.background = dec.color_hex || '#1e293b';
      document.getElementById('binName').textContent = dec.bin_name;
      document.getElementById('binStatus').textContent = dec.status;
      document.getElementById('kClass').textContent = dec.predicted_class;
      document.getElementById('kConf').textContent = `${(dec.confidence * 100).toFixed(1)}%`;
      document.getElementById('kLat').textContent = vis ? `${vis.inference_ms} ms` : '0.0 ms';
      document.getElementById('kEnt').textContent = dec.entropy !== null ? dec.entropy : '—';

      const L = dec.labels || {};
      document.getElementById('langStrip').textContent = `EN: ${L.en || ''} | HI: ${L.hi || ''} | KN: ${L.kn || ''}`;

      if (gt) {
        const ok = dec.stream === gt.statutory_stream;
        const b = document.getElementById('gtBadge');
        b.textContent = `GT: ${gt.true_class} ${ok ? '✓' : '✗'}`;
      }
    }

    async function selectSample(idx) {
      currentIdx = idx;
      reqToken++;
      const tok = reqToken;
      document.querySelectorAll('.item-row').forEach(r => r.classList.remove('active'));
      const row = document.getElementById(`row-${idx}`);
      if (row) { row.classList.add('active'); row.scrollIntoView({ block:'nearest' }); }

      const it = items[idx];
      const res = await (await fetch('/api/infer_sample', {
        method: 'POST', headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({ filename: it.filename })
      })).json();
      if (tok !== reqToken) return;
      renderVision(res, `/benchmark30/${encodeURIComponent(it.filename)}`, it);
      if (document.getElementById('autoLlama').checked) askLlama(true, tok);
    }

    async function uploadCustom(ev) {
      const file = ev.target.files[0];
      if (!file) return;
      reqToken++;
      const tok = reqToken;
      document.getElementById('gtBadge').textContent = file.name;
      const buf = await file.arrayBuffer();
      const res = await (await fetch('/api/infer', { method: 'POST', body: buf })).json();
      if (tok !== reqToken) return;
      renderVision(res, URL.createObjectURL(file), null);
      if (document.getElementById('autoLlama').checked) askLlama(true, tok);
    }

    async function triggerSanitary() {
      reqToken++;
      const tok = reqToken;
      const res = await (await fetch('/api/sanitary-override', { method: 'POST' })).json();
      renderVision(res, '', null);
      if (document.getElementById('autoLlama').checked) askLlama(true, tok);
    }

    async function askLlama(isDefault = false, passedTok = null) {
      if (!currentDecision) return;
      const tok = passedTok || ++reqToken;
      const q = isDefault ? '' : document.getElementById('chatInput').value.trim();
      if (!isDefault && !q) return;

      document.getElementById('llamaBox').textContent = 'Generating Llama 3.2 1B response...';
      document.getElementById('llamaTiming').textContent = 'Running...';
      const res = await (await fetch('/api/chat', {
        method: 'POST', headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({ question: q, decision: currentDecision })
      })).json();
      if (tok !== reqToken) return;
      document.getElementById('llamaBox').textContent = res.error ? res.error : res.answer;
      document.getElementById('llamaTiming').textContent = res.error ? 'Error' : `${res.latency_ms} ms · ${res.tokens_per_sec_est} tok/s · cache=${res.cache_hit}`;
    }

    async function runBatch30() {
      switchTab('paneBatch', document.querySelectorAll('.tab-btn')[1]);
      document.getElementById('batchSummary').textContent = 'Running Vision INT8 across 30 images...';
      const d = await (await fetch('/api/run_batch30')).json();
      document.getElementById('batchSummary').textContent =
        `Class Acc: ${d.class_accuracy_pct}% · SWM Compliance: ${d.statutory_compliance_pct}% · Battery Recall: ${d.battery_hazard_recall_pct}% · Avg: ${d.avg_inference_ms} ms`;
      document.getElementById('batchBody').innerHTML = d.results.map(r => `
        <tr>
          <td>${r.sample_id}</td>
          <td>${r.true_class}</td>
          <td class="${r.class_match ? 'ok-t' : 'err-t'}">${r.predicted_class}</td>
          <td>${(r.confidence * 100).toFixed(1)}%</td>
          <td class="${r.stream_match ? 'ok-t' : 'err-t'}">${r.stream}</td>
          <td>${r.inference_ms}</td>
        </tr>
      `).join('');
    }

    init();
  </script>
</body>
</html>
"""


def get_soc_telemetry() -> dict:
    telem = {"cpu_temp_c": None, "volts_v": None, "power_w_est": None}
    try:
        t_out = subprocess.check_output(["vcgencmd", "measure_temp"], text=True, timeout=0.5).strip()
        telem["cpu_temp_c"] = float(t_out.split("=")[1].replace("'C", ""))
    except Exception:
        pass
    try:
        v_out = subprocess.check_output(["vcgencmd", "measure_volts", "core"], text=True, timeout=0.5).strip()
        volts = float(v_out.split("=")[1].replace("V", ""))
        telem["volts_v"] = volts
        telem["power_w_est"] = round(5.5 * volts, 2)
    except Exception:
        pass
    return telem


class EdgeAppState:
    def __init__(self):
        self.vision = VisionEngine()
        self.policy = PolicyGuard()
        self.llama = LlamaExecuTorchEngine()
        self.latest_result = None


STATE: EdgeAppState = None


class EdgeAPIHandler(BaseHTTPRequestHandler):
    def log_message(self, format, *args):
        pass

    def _send_json(self, code: int, payload: dict):
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()
        self.wfile.write(body)

    def _send_bytes(self, code: int, content_type: str, data: bytes):
        self.send_response(code)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        if self.path == "/" or self.path == "/index.html":
            self._send_bytes(200, "text/html; charset=utf-8", DASHBOARD_HTML.encode("utf-8"))
        elif self.path == "/health" or self.path == "/api/status":
            self._send_json(200, {
                "status": "online_airgapped",
                "vision_model": STATE.vision.model_path.name,
                "vision_load_ms": round(STATE.vision.load_latency_ms, 2),
                "llama_ready": STATE.llama is not None,
                "llama_model": STATE.llama.model_path.name,
                "llama_backend": STATE.llama.backend_mode,
                "telemetry": get_soc_telemetry(),
            })
        elif self.path == "/api/benchmark30":
            gt_path = BENCHMARK_30_DIR / "ground_truth.json"
            items = json.loads(gt_path.read_text(encoding="utf-8")) if gt_path.exists() else []
            self._send_json(200, {"count": len(items), "items": items})
        elif self.path.startswith("/benchmark30/"):
            fname = unquote(self.path.split("/benchmark30/", 1)[1])
            fpath = (BENCHMARK_30_DIR / fname).resolve()
            if fpath.parent != BENCHMARK_30_DIR.resolve() or not fpath.exists():
                self._send_json(404, {"error": "Image not found"})
                return
            ctype = "image/png" if fpath.suffix.lower() == ".png" else "image/jpeg"
            self._send_bytes(200, ctype, fpath.read_bytes())
        elif self.path == "/api/run_batch30":
            gt_path = BENCHMARK_30_DIR / "ground_truth.json"
            gt_items = json.loads(gt_path.read_text(encoding="utf-8"))
            rows, lats = [], []
            cls_ok = stream_ok = haz_tot = haz_ok = 0
            for item in gt_items:
                frame = cv2.imread(str(BENCHMARK_30_DIR / item["filename"]))
                vis = STATE.vision.classify_frame(frame)
                dec = STATE.policy.evaluate(vis["predicted_class"], vis["confidence"], vis["entropy"])
                lats.append(vis["inference_ms"])
                c_m = vis["predicted_class"] == item["true_class"]
                s_m = dec["stream"] == item["statutory_stream"]
                cls_ok += int(c_m)
                stream_ok += int(s_m)
                if item["statutory_stream"] == "special_care":
                    haz_tot += 1
                    haz_ok += int(dec["stream"] == "special_care")
                rows.append({
                    "sample_id": item["sample_id"],
                    "filename": item["filename"],
                    "true_class": item["true_class"],
                    "predicted_class": vis["predicted_class"],
                    "confidence": round(vis["confidence"], 4),
                    "stream": dec["stream"],
                    "class_match": c_m,
                    "stream_match": s_m,
                    "inference_ms": vis["inference_ms"],
                })
            total = len(rows)
            self._send_json(200, {
                "samples": total,
                "class_accuracy_pct": round((cls_ok / total) * 100.0, 2),
                "statutory_compliance_pct": round((stream_ok / total) * 100.0, 2),
                "battery_hazard_recall_pct": round((haz_ok / haz_tot) * 100.0, 2) if haz_tot else None,
                "avg_inference_ms": round(float(np.mean(lats)), 2),
                "results": rows,
            })
        elif self.path == "/api/latest":
            self._send_json(200, STATE.latest_result or {"status": "idle"})
        else:
            self._send_json(404, {"error": "Endpoint not found"})

    def do_POST(self):
        length = int(self.headers.get("Content-Length", 0))
        raw_body = self.rfile.read(length) if length > 0 else b""

        if self.path == "/api/sanitary-override":
            decision = STATE.policy.evaluate("sanitary_item", 1.0, sanitary_button=True)
            res = {"vision": None, "decision": decision}
            STATE.latest_result = res
            self._send_json(200, res)
            return

        if self.path == "/api/infer_sample":
            data = json.loads(raw_body.decode("utf-8"))
            fpath = (BENCHMARK_30_DIR / data.get("filename", "")).resolve()
            if fpath.parent != BENCHMARK_30_DIR.resolve() or not fpath.exists():
                self._send_json(404, {"error": "Sample not found"})
                return
            frame = cv2.imread(str(fpath))
        elif self.path == "/api/infer":
            np_arr = np.frombuffer(raw_body, np.uint8)
            frame = cv2.imdecode(np_arr, cv2.IMREAD_COLOR)
        elif self.path == "/api/chat":
            data = json.loads(raw_body.decode("utf-8"))
            decision = data.get("decision") or (STATE.latest_result or {}).get("decision")
            if not decision:
                self._send_json(400, {"error": "No item context available."})
                return
            advisory = STATE.llama.generate(
                decision=decision,
                user_question=data.get("question", ""),
                language=data.get("language", "en"),
            )
            self._send_json(200, advisory)
            return
        else:
            self._send_json(404, {"error": "Endpoint not found"})
            return

        if frame is None:
            self._send_json(400, {"error": "Invalid image bytes."})
            return

        vis = STATE.vision.classify_frame(frame)
        dec = STATE.policy.evaluate(vis["predicted_class"], vis["confidence"], vis["entropy"])
        res = {"vision": vis, "decision": dec}
        STATE.latest_result = res
        self._send_json(200, res)


def main():
    global STATE
    parser = argparse.ArgumentParser(description="EcoSort-Edge 2.0 INT8 Dashboard & API Server")
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=8080)
    args = parser.parse_args()

    print("[*] Initializing Vision INT8 & Llama 3.2 1B INT8...")
    STATE = EdgeAppState()
    server = HTTPServer((args.host, args.port), EdgeAPIHandler)
    print(f"[*] EcoSort-Edge 2.0 Dashboard running at http://localhost:{args.port}")
    print(f"[*] Vision INT8 Load: {STATE.vision.load_latency_ms:.2f} ms | Llama Ready: True ({STATE.llama.backend_mode})")
    server.serve_forever()


if __name__ == "__main__":
    main()
