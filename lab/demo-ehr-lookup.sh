#!/usr/bin/env sh
# Smoke-test FHIR + HL7 lab harnesses (curl only).
set -eu

FHIR_BASE="${FHIR_BASE_URL:-http://127.0.0.1:8080/fhir}"
HL7_BASE="${HL7_BASE_URL:-http://127.0.0.1:8090}"

fail() { echo "FAIL: $*" >&2; exit 1; }
ok() { echo "OK: $*"; }

echo "=== FHIR Patient lookup (999888777) ==="
fhir_body="$(curl -sf -H 'Accept: application/fhir+json' \
  "$FHIR_BASE/Patient?identifier=999888777")" \
  || fail "FHIR request failed — is HAPI up and seeded?"

echo "$fhir_body" | grep -q 'de Vries' || fail "FHIR response missing de Vries"
echo "$fhir_body" | grep -q '999888777' || fail "FHIR response missing identifier"
ok "FHIR returned Patient for 999888777"

echo "=== HL7 façade lookup (123456789) ==="
hl7_body="$(curl -sf -H 'Accept: application/json' \
  "$HL7_BASE/patients?patientId=123456789")" \
  || fail "HL7 request failed — is the façade up?"

echo "$hl7_body" | grep -q 'JANSEN' || fail "HL7 response missing JANSEN"
echo "$hl7_body" | grep -q '123456789' || fail "HL7 response missing patientId"
ok "HL7 returned demographics for 123456789"

echo
echo "Both EHR demo harnesses respond correctly."
echo "Configure the app (emulator host 10.0.2.2):"
echo "  FHIR base:  http://10.0.2.2:8080/fhir"
echo "  HL7 base:   http://10.0.2.2:8090"
echo "Then Worklist → Manual → Look up in EHR."
