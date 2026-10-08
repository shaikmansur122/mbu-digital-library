// Code editor component used by lab experiments, the faculty "view submission" page and the practice Editor.
// Uses Monaco (the VS Code editor) when it can be loaded and falls back to a plain textarea otherwise.
// Python and SQL run inside the browser; Java, C and C++ are sent to the server's /labs/run endpoint.
(function () {
  'use strict';

  var SCRIPT_BASE = document.currentScript.src.replace(/code-editor\.js.*$/, '');
  var MONACO_BASE = 'https://cdn.jsdelivr.net/npm/monaco-editor@0.52.0/min/vs';
  var SQLJS_BASE = 'https://cdn.jsdelivr.net/npm/sql.js@1.10.3/dist/';
  var MONACO_LANG = { PYTHON: 'python', JAVA: 'java', CPP: 'cpp', C: 'c', SQL: 'sql' };

  var STARTERS = {
    PYTHON: 'print("Hello, MBU!")\n',
    JAVA: 'public class Main {\n    public static void main(String[] args) {\n        System.out.println("Hello, MBU!");\n    }\n}\n',
    CPP: '#include <iostream>\nusing namespace std;\n\nint main() {\n    cout << "Hello, MBU!" << endl;\n    return 0;\n}\n',
    C: '#include <stdio.h>\n\nint main() {\n    printf("Hello, MBU!\\n");\n    return 0;\n}\n',
    SQL: "CREATE TABLE students (id INTEGER PRIMARY KEY, name TEXT, marks INTEGER);\nINSERT INTO students (name, marks) VALUES ('Asha', 82), ('Ravi', 74);\nSELECT * FROM students;\n"
  };

  // ---------------- Monaco (loaded once, on demand) ----------------
  var monacoPromise = null;
  function loadMonaco() {
    if (!monacoPromise) {
      monacoPromise = new Promise(function (resolve, reject) {
        window.MonacoEnvironment = {
          getWorkerUrl: function () {
            return 'data:text/javascript;charset=utf-8,' + encodeURIComponent(
              "self.MonacoEnvironment = { baseUrl: '" + MONACO_BASE + "/' };" +
              "importScripts('" + MONACO_BASE + "/base/worker/workerMain.js');");
          }
        };
        var s = document.createElement('script');
        s.src = MONACO_BASE + '/loader.js';
        s.onload = function () {
          window.require.config({ paths: { vs: MONACO_BASE } });
          window.require(['vs/editor/editor.main'], function () { resolve(window.monaco); }, reject);
        };
        s.onerror = reject;
        document.head.appendChild(s);
      });
    }
    return monacoPromise;
  }

  // ---------------- Python (Pyodide in a worker) ----------------
  var pyWorker = null;
  var pyReady = null;
  var pyPending = {};
  var pyCounter = 0;

  function startPython() {
    pyWorker = new Worker(SCRIPT_BASE + 'python-worker.js');
    pyPending = {};
    pyReady = new Promise(function (resolve, reject) {
      pyWorker.onmessage = function (e) {
        var d = e.data;
        if (d.ready) { resolve(); return; }
        var cb = pyPending[d.id];
        if (cb) { delete pyPending[d.id]; cb(d); }
      };
      pyWorker.onerror = function () { reject(new Error('worker failed')); };
    });
  }

  function stopPython() {
    if (pyWorker) { pyWorker.terminate(); }
    pyWorker = null;
    pyReady = null;
    pyPending = {};
  }

  function runPython(code, stdin) {
    if (!pyWorker) { startPython(); }
    return new Promise(function (resolve) {
      var id = ++pyCounter;
      var done = false;
      var runTimer = null;
      var loadTimer = setTimeout(function () {
        finish({ note: 'Python could not be loaded in time. Check your internet connection and try again.' }, true);
      }, 120000);

      function finish(result, reset) {
        if (done) { return; }
        done = true;
        clearTimeout(loadTimer);
        clearTimeout(runTimer);
        if (reset) { stopPython(); }
        resolve(result);
      }

      pyReady.then(function () {
        if (done) { return; }
        clearTimeout(loadTimer);
        pyPending[id] = function (d) { finish({ stdout: d.out, stderr: d.err }, false); };
        runTimer = setTimeout(function () {
          finish({ note: 'Stopped: the program ran for more than 45 seconds (infinite loop?).' }, true);
        }, 45000);
        pyWorker.postMessage({ id: id, code: code, stdin: stdin });
      }, function () {
        finish({ note: 'Python could not be loaded. Check your internet connection and try again.' }, true);
      });
    });
  }

  // ---------------- SQL (sql.js, SQLite in the browser) ----------------
  var sqlPromise = null;
  function loadSql() {
    if (!sqlPromise) {
      sqlPromise = new Promise(function (resolve, reject) {
        var s = document.createElement('script');
        s.src = SQLJS_BASE + 'sql-wasm.js';
        s.onload = function () {
          window.initSqlJs({ locateFile: function (f) { return SQLJS_BASE + f; } }).then(resolve, reject);
        };
        s.onerror = reject;
        document.head.appendChild(s);
      });
      sqlPromise.catch(function () { sqlPromise = null; });
    }
    return sqlPromise;
  }

  function formatSql(results) {
    if (!results.length) {
      return 'Statements ran successfully (no rows returned).\n' +
        'Note: every run starts with an empty database, so CREATE and INSERT your tables in the same script.\n';
    }
    return results.map(function (r) {
      var rows = r.values.map(function (row) {
        return row.map(function (v) { return v === null ? 'NULL' : String(v); });
      });
      var widths = r.columns.map(function (c, i) {
        return Math.max.apply(null, [c.length].concat(rows.map(function (row) { return row[i].length; })));
      });
      var pad = function (cells) {
        return cells.map(function (c, i) { return c + ' '.repeat(widths[i] - c.length); }).join(' | ');
      };
      var line = widths.map(function (w) { return '-'.repeat(w); }).join('-+-');
      return [pad(r.columns), line].concat(rows.map(pad)).join('\n') + '\n(' + rows.length + ' row' + (rows.length === 1 ? '' : 's') + ')\n';
    }).join('\n');
  }

  function runSql(code) {
    return loadSql().then(function (SQL) {
      var db = new SQL.Database();
      try {
        return { stdout: formatSql(db.exec(code)) };
      } catch (e) {
        return { stderr: String(e && e.message ? e.message : e) + '\n' };
      } finally {
        db.close();
      }
    }, function () {
      return { note: 'The SQL engine could not be loaded. Check your internet connection and try again.' };
    });
  }

  // ---------------- Java / C / C++ (server) ----------------
  function runServer(url, csrf, language, code, stdin) {
    return fetch(url, {
      method: 'POST',
      credentials: 'same-origin',
      headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': csrf },
      body: JSON.stringify({ language: language, code: code, stdin: stdin })
    }).then(function (res) {
      if (!res.ok) { throw new Error('server answered ' + res.status); }
      return res.json();
    }).then(function (r) {
      var notes = [];
      if (r.problem && !r.stderr) { notes.push(r.problem); }
      if (r.timedOut) { notes.push('Stopped: the program ran longer than the time limit.'); }
      if (r.outputTruncated) { notes.push('Output was cut off (too long).'); }
      if (!r.problem && !r.timedOut && r.exitCode) {
        // 0xC0000005 / 0xC00000FD are Windows access-violation and stack-overflow codes (segfault-style crashes)
        var crashed = r.exitCode === -1073741819 || r.exitCode === -1073741571 || r.exitCode === 139 || r.exitCode === 134;
        notes.push(crashed ? 'The program crashed (invalid memory access or stack overflow).' : 'Exit code ' + r.exitCode);
      }
      return { stdout: r.stdout, stderr: r.stderr, note: notes.join('\n') };
    });
  }

  // ---------------- The component ----------------
  function init(root) {
    var textarea = root.querySelector('.code-source');
    var surface = root.querySelector('.editor-surface');
    var runBtn = root.querySelector('.run-btn');
    var status = root.querySelector('.run-status');
    var output = root.querySelector('.output');
    var stdin = root.querySelector('.stdin');
    var select = root.querySelector('.lang-select');
    var readOnly = root.dataset.readonly === 'true';
    var language = select ? select.value : root.dataset.language;
    var editor = null;
    var monacoRef = null;
    var lastStarter = null;

    function getCode() { return editor ? editor.getValue() : textarea.value; }
    function setCode(v) {
      if (editor) { editor.setValue(v); } else { textarea.value = v; }
      textarea.value = v;
    }

    if (select && textarea.value.trim() === '') {
      textarea.value = STARTERS[language];
    }
    lastStarter = select ? STARTERS[language] : null;
    textarea.readOnly = readOnly;

    loadMonaco().then(function (monaco) {
      monacoRef = monaco;
      surface.hidden = false;
      textarea.hidden = true;
      editor = monaco.editor.create(surface, {
        value: textarea.value,
        language: MONACO_LANG[language],
        readOnly: readOnly,
        automaticLayout: true,
        minimap: { enabled: false },
        fontSize: 14,
        scrollBeyondLastLine: false,
        tabSize: 4
      });
      editor.onDidChangeModelContent(function () { textarea.value = editor.getValue(); });
    }).catch(function () {
      root.classList.add('plain');   // keep the textarea; the editor still works, just without highlighting
    });

    // make sure a surrounding form submits what is in the editor
    var form = root.closest('form');
    if (form) {
      form.addEventListener('submit', function () { textarea.value = getCode(); });
    }

    if (select) {
      select.addEventListener('change', function () {
        language = select.value;
        if (editor && monacoRef) { monacoRef.editor.setModelLanguage(editor.getModel(), MONACO_LANG[language]); }
        var current = getCode();
        if (current.trim() === '' || current === lastStarter) {
          setCode(STARTERS[language]);
        }
        lastStarter = STARTERS[language];
      });
    }

    function show(result) {
      output.textContent = '';
      function add(text, cls) {
        if (!text) { return; }
        var span = document.createElement('span');
        if (cls) { span.className = cls; }
        span.textContent = text;
        output.appendChild(span);
      }
      add(result.stdout, '');
      add(result.stderr, 'err');
      if (result.note) { add((output.textContent ? '\n' : '') + result.note + '\n', 'note'); }
      if (!output.textContent) { add('(The program printed nothing.)', 'note'); }
    }

    runBtn.addEventListener('click', function () {
      var code = getCode();
      if (code.trim() === '') {
        show({ note: 'Write some code first.' });
        return;
      }
      runBtn.disabled = true;
      status.textContent = language === 'PYTHON' ? 'Running (the first run downloads Python, this can take a few seconds)…' : 'Running…';
      var input = stdin ? stdin.value : '';
      var promise;
      if (language === 'PYTHON') { promise = runPython(code, input); }
      else if (language === 'SQL') { promise = runSql(code); }
      else { promise = runServer(root.dataset.runUrl, root.dataset.csrf, language, code, input); }
      promise.then(show, function (e) {
        show({ note: 'Could not run the program: ' + (e && e.message ? e.message : e) });
      }).then(function () {
        runBtn.disabled = false;
        status.textContent = '';
      });
    });
  }

  document.querySelectorAll('.code-editor').forEach(init);
})();
