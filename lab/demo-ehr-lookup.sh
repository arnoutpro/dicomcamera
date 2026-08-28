#!/usr/bin/env sh
# Smoke-test FHIR + HL7 lab harnesses (curl only).
# Retries: HAPI is slow on first boot and fhir-seed runs after compose returns.
set -eu

FHIR_BASE="${FHIR_BASE_URL:-http://127.0.0.1:8080/fhir}"
HL7_BASE="${HL7_BASE_URL:-http://127.0.0.1:8090}"
TRIES="${EHR_LOOKUP_TRIES:-60}"
SLEEP="${EHR_LOOKUP_SLEEP:-3}"

fail() { echo "FAIL: $*" >&2; exit 1; }
ok() { echo "OK: $*"; }

wait_for() {
  name="$1"
  needle="$2"
  shift 2
  i=1
  while [ "$i" -le "$TRIES" ]; do
    body="$(curl -sf "$@" 2>/dev/null || true)"
    if [ -n "$body" ] && echo "$body" | grep -q "$needle"; then
      echo "$body"
      return 0
    fi
    echo "waiting for $name ($i/$TRIES)…"
    i=$((i + 1))
    sleep "$SLEEP"
  done
  return 1
}

echo "=== FHIR Patient lookup (999888777) ==="
fhir_body="$(wait_for FHIR "de Vries" \
  -H "Accept: application/fhir+json" \
  "$FHIR_BASE/Patient?identifier=999888777")" \
  || fail "FHIR request failed — is HAPI up and seeded? (docker compose logs fhir fhir-seed)"

echo "$fhir_body" | grep -q '999888777' || fail "FHIR response missing identifier"
ok "FHIR returned Patient for 999888777"

echo "=== HL7 façade lookup (123456789) ==="
hl7_body="$(wait_for HL7 "JANSEN" \
  -H "Accept: application/json" \
  "$HL7_BASE/patients?patientId=123456789")" \
  || fail "HL7 request failed — is the façade up? (docker compose logs hl7-facade)"

echo "$hl7_body" | grep -q '123456789' || fail "HL7 response missing patientId"
ok "HL7 returned demographics for 123456789"

echo
echo "Both EHR demo harnesses respond correctly."
echo "Use the **dev** flavor APK (staging blocks cleartext HTTP)."
echo "Configure the app (emulator host 10.0.2.2):"
echo "  FHIR base:  http://10.0.2.2:8080/fhir"
echo "  HL7 base:   http://10.0.2.2:8090"
echo "Then Worklist → Manual → Look up in EHR."
