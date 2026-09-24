// Build façade JSON after an upstream lookup (DB / LLP RSP / channelMap).
// Expect demographics already in channelMap (adjust keys to your site).

function dig(key, fallback) {
  try {
    var v = channelMap.get(key);
    if (v !== null && v !== undefined && (v + '') !== '') return (v + '').trim();
  } catch (e) {}
  return fallback || '';
}

var patientId = dig('patientId', dig('pid', ''));
var family = dig('patientFamily', dig('familyName', ''));
var given = dig('patientGiven', dig('givenName', ''));
var birthDate = dig('birthDate', dig('dob', '')); // YYYYMMDD
var sex = dig('sex', dig('gender', 'O')).toUpperCase();
if (sex === 'MALE') sex = 'M';
if (sex === 'FEMALE') sex = 'F';
if (sex !== 'M' && sex !== 'F' && sex !== 'O') sex = 'O';

var body;
if (!patientId) {
  body = { patients: [] };
} else {
  var pn = family;
  if (given) pn = family ? (family + '^' + given) : given;
  body = {
    patientId: patientId,
    patientName: pn, // DICOM PN FAMILY^GIVEN
    birthDate: birthDate,
    sex: sex
  };
}

var json = JSON.stringify(body);
channelMap.put('responseJson', json);
try { responseMap.put('response', json); } catch (e) {}
return json;
