// Runs Python in a Web Worker using Pyodide (Python compiled to WebAssembly), so student code
// executes inside the browser and an infinite loop can be stopped by terminating the worker.
importScripts('https://cdn.jsdelivr.net/pyodide/v0.26.4/full/pyodide.js');

var pyodideReady = loadPyodide().then(function (py) {
  // Library deprecation notices (e.g. pandas/pyarrow) only confuse students; real errors still show.
  py.runPython(
    "import warnings\n" +
    "warnings.filterwarnings('ignore', category=DeprecationWarning)\n" +
    "warnings.filterwarnings('ignore', category=FutureWarning)\n");
  postMessage({ ready: true });
  return py;
});

onmessage = async function (event) {
  var msg = event.data;
  var py = await pyodideReady;
  var out = '';
  var err = '';
  var lines = String(msg.stdin || '').split('\n');
  var next = 0;

  py.setStdout({ batched: function (s) { out += s + '\n'; } });
  py.setStderr({ batched: function (s) { err += s + '\n'; } });
  py.setStdin({ stdin: function () { return next < lines.length ? lines[next++] : undefined; } });

  try {
    // Data Science labs: fetch numpy, pandas, etc. when the code imports them.
    await py.loadPackagesFromImports(msg.code);
    await py.runPythonAsync(msg.code);
  } catch (e) {
    err += String(e && e.message ? e.message : e) + '\n';
  }
  postMessage({ id: msg.id, out: out, err: err });
};
