// Optional Mirth source filter — only allow demographics lookups.
// Adapt URL key names to what your HTTP Listener puts in the source map.

var url = '';
try { url = ($('httpRequestUrl') + ''); } catch (e) { url = ''; }
if (!url) {
  try { url = (sourceMap.get('method') + ' ' + sourceMap.get('contextPath')); } catch (e2) {}
}

url = (url + '').toLowerCase();
if (url.indexOf('/patients') < 0) {
  return false;
}
// Prefer GET only when method is available
try {
  var method = (sourceMap.get('method') + '').toUpperCase();
  if (method && method !== 'GET') return false;
} catch (e3) {}

return true;
