#!/usr/bin/env python3
"""Minimal HL7 demographics HTTPS-façade mock for DicomCamera lab demos.

Contract: GET /patients?patientId=… → JSON matching docs/deploy/HL7_FACADE_CONTRACT.md
No real MLLP — this stands in for the hospital interface-engine façade.
"""

from __future__ import annotations

import json
import os
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlparse

PORT = int(os.environ.get("HL7_FACADE_PORT", "8090"))
DATA_PATH = Path(os.environ.get("HL7_PATIENTS_FILE", Path(__file__).with_name("patients.json")))


def load_patients() -> dict[str, dict]:
    raw = json.loads(DATA_PATH.read_text(encoding="utf-8"))
    by_id: dict[str, dict] = {}
    for item in raw.get("patients", []):
        pid = str(item.get("patientId", "")).strip()
        if pid:
            by_id[pid] = item
    return by_id


PATIENTS = load_patients()


class Handler(BaseHTTPRequestHandler):
    def log_message(self, fmt: str, *args) -> None:  # quieter docker logs
        sys_stderr = __import__("sys").stderr
        sys_stderr.write("%s - %s\n" % (self.address_string(), fmt % args))

    def _send(self, code: int, payload: object) -> None:
        body = json.dumps(payload).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()
        self.wfile.write(body)

    def do_OPTIONS(self) -> None:  # noqa: N802
        self.send_response(204)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Authorization, Accept, Content-Type")
        self.end_headers()

    def do_GET(self) -> None:  # noqa: N802
        parsed = urlparse(self.path)
        path = parsed.path.rstrip("/") or "/"
        qs = parse_qs(parsed.query)

        if path in ("/", "/health"):
            self._send(200, {"status": "ok", "patients": len(PATIENTS)})
            return

        if path != "/patients":
            self._send(404, {"error": "not found", "path": path})
            return

        patient_id = (qs.get("patientId") or [""])[0].strip()
        if not patient_id:
            self._send(400, {"error": "patientId required"})
            return

        match = PATIENTS.get(patient_id)
        if match is None:
            # Empty list = "not found" for the app (not HTTP 404)
            self._send(200, {"patients": []})
            return

        self._send(200, match)


def main() -> None:
    server = ThreadingHTTPServer(("0.0.0.0", PORT), Handler)
    print(f"HL7 façade mock listening on :{PORT} with {len(PATIENTS)} patients", flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
