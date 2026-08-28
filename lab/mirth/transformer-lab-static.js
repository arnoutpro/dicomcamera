// Mirth destination transformer / JavaScript Writer — LAB ONLY
// Implements GET …/patients?patientId= for DicomCamera HL7 façade contract.
// Seed IDs match lab/hl7-facade/patients.json

var url = '';
try {
  url = $('httpRequestUrl') + '';
} catch (e) {
  url = '';
}
if (!url) {
  try { url = sourceMap.get('httpRequestUrl') + ''; } catch (e2) { url = ''; }
}

var patientId = '';
var q = url.indexOf('patientId=');
if (q >= 0) {
  patientId = url.substring(q + 'patientId='.length).split('&')[0];
  try { patientId = decodeURIComponent(patientId); } catch (e3) {}
}
patientId = (patientId + '').trim();

var patients = {
  '123456789': {
    patientId: '123456789',
    patientName: 'JANSEN^ANNE',
    birthDate: '19800315',
    sex: 'F'
  },
  '555666777': {
    patientId: '555666777',
    patientName: 'DE BOER^KEES',
    birthDate: '19750120',
    sex: 'M'
  }
};

var body;
if (!patientId) {
  responseStatus = 400;
  body = { error: 'patientId required' };
} else if (patients[patientId]) {
  responseStatus = 200;
  body = patients[patientId];
} else {
  responseStatus = 200;
  body = { patients: [] };
}

var json = JSON.stringify(body);
channelMap.put('responseJson', json);

// Prefer setting the HTTP response body via Response Map in your Mirth version:
try {
  responseMap.put('response', json);
} catch (e4) {}

return json;
