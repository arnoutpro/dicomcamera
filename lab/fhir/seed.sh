#!/usr/bin/env sh
# Seed sample Patients into HAPI FHIR (R4).
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
for i in range(90):
    try:
        with urllib.request.urlopen(url, timeout=3) as resp:
            if 200 <= resp.status < 300:
                print("HAPI is up.")
                break
    except Exception:
        pass
    time.sleep(2)
else:
    raise SystemExit(f"Timed out waiting for {url}")
PY

echo "Seeding patients from $SEED_FILE"
python3 <<'PY'
import json
import os
import urllib.error
import urllib.request

base = os.environ["FHIR_BASE_URL"].rstrip("/")
path = os.environ["SEED_FILE"]
patients = json.load(open(path, encoding="utf-8"))
for p in patients:
    mrn = p["identifier"][0]["value"]
    data = json.dumps(p).encode("utf-8")
    req = urllib.request.Request(
        f"{base}/Patient",
        data=data,
        headers={
            "Content-Type": "application/fhir+json",
            "Accept": "application/fhir+json",
        },
        method="POST",
    )
    try:
        with urllib.request.urlopen(req) as resp:
            print(f"  POST Patient {mrn} → HTTP {resp.status}")
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8", errors="replace")
        print(f"  POST Patient {mrn} → HTTP {e.code}: {body[:300]}")
        raise
print("Seed complete.")
PY
