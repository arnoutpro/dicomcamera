#!/usr/bin/env sh
# Seed sample Patients into HAPI FHIR (R4). Idempotent: skip IDs that already exist.
set -eu

BASE="${FHIR_BASE_URL:-http://127.0.0.1:8080/fhir}"
SEED_FILE="${SEED_FILE:-$(CDPATH= cd -- "$(dirname "$0")" && pwd)/seed-patients.json}"
export FHIR_BASE_URL="$BASE"
export SEED_FILE

echo "Waiting for FHIR at $BASE/metadata …"
python3 <<'PY'
import os
import time
import urllib.error
import urllib.request

base = os.environ["FHIR_BASE_URL"].rstrip("/")
url = f"{base}/metadata"
deadline = time.time() + 180
while time.time() < deadline:
    try:
        with urllib.request.urlopen(url, timeout=5) as resp:
            if 200 <= resp.status < 300:
                print("HAPI is up.")
                break
    except Exception:
        pass
    time.sleep(3)
else:
    raise SystemExit(f"Timed out waiting for {url}")
PY

echo "Seeding patients from $SEED_FILE"
python3 <<'PY'
import json
import os
import urllib.error
import urllib.parse
import urllib.request

base = os.environ["FHIR_BASE_URL"].rstrip("/")
path = os.environ["SEED_FILE"]
patients = json.load(open(path, encoding="utf-8"))


def http_json(method: str, url: str, data: bytes | None = None):
    headers = {"Accept": "application/fhir+json"}
    if data is not None:
        headers["Content-Type"] = "application/fhir+json"
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    with urllib.request.urlopen(req) as resp:
        body = resp.read().decode("utf-8")
        return resp.status, body


for p in patients:
    mrn = p["identifier"][0]["value"]
    search = f"{base}/Patient?identifier={urllib.parse.quote(mrn, safe='')}"
    try:
        status, body = http_json("GET", search)
        total = json.loads(body).get("total", 0) if body else 0
        if total:
            print(f"  skip Patient {mrn} (already present, total={total})")
            continue
    except urllib.error.HTTPError as e:
        print(f"  GET Patient {mrn} → HTTP {e.code}; will POST")

    data = json.dumps(p).encode("utf-8")
    try:
        status, _ = http_json("POST", f"{base}/Patient", data)
        print(f"  POST Patient {mrn} → HTTP {status}")
    except urllib.error.HTTPError as e:
        err = e.read().decode("utf-8", errors="replace")
        print(f"  POST Patient {mrn} → HTTP {e.code}: {err[:300]}")
        raise
print("Seed complete.")
PY
