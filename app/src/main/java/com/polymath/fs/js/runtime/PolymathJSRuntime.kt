package com.polymath.fs.js.runtime

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import app.cash.quickjs.QuickJs
import com.polymath.fs.core.KernelEngineController
import com.polymath.fs.core.RootShellHolder
import com.polymath.fs.data.repository.FileSystemRepository
import com.polymath.fs.js.*
import com.polymath.fs.js.runtime.modules.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Polymath Real JavaScript OS Engine Runtime.
 * Delivers an unrestricted, high-performance Node.js / POSIX-like OS execution environment.
 * Eliminates artificial sandboxing, providing raw file system, process execution, 
 * kernel introspection, real HTTP networking, and CommonJS module resolution.
 */
@Singleton
class PolymathJSRuntime @Inject constructor(
    private val context: Context,
    private val repository: FileSystemRepository,
    private val shellHolder: RootShellHolder = RootShellHolder(),
    private val kernelController: KernelEngineController = KernelEngineController()
) {

    private val fsModule = FsNativeModule()
    private val osModule = OsNativeModule(context, kernelController)
    private val processModule = ProcessNativeModule(context, shellHolder)
    private val httpModule = HttpNativeModule()
    private val cryptoModule = CryptoNativeModule()
    private val kernelModule = KernelNativeModule(kernelController, shellHolder)
    private val moduleLoader = PolymathModuleLoader(context)

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Executes JavaScript code within the real OS execution environment.
     * No artificial permission barriers or sandboxes.
     */
    fun execute(
        script: String,
        scriptName: String = "script.js",
        workingDir: String = "/storage/emulated/0",
        selectedFiles: List<String>? = null,
        onAlert: ((title: String, message: String) -> Unit)? = null,
        onConsoleLog: ((level: String, message: String) -> Unit)? = null
    ): String {
        QuickJs.create().use { quickJs ->
            initializeEnvironment(
                quickJs = quickJs,
                scriptName = scriptName,
                workingDir = workingDir,
                selectedFiles = selectedFiles,
                onAlert = onAlert,
                onConsoleLog = onConsoleLog
            )

            val evaluated = quickJs.evaluate(script)
            return evaluated?.toString() ?: ""
        }
    }

    private fun initializeEnvironment(
        quickJs: QuickJs,
        scriptName: String,
        workingDir: String,
        selectedFiles: List<String>?,
        onAlert: ((title: String, message: String) -> Unit)?,
        onConsoleLog: ((level: String, message: String) -> Unit)?
    ) {
        processModule.setCwd(workingDir)

        // 1. Bind low-level native modules
        quickJs.set("_posix", PolymathJSPOSIXInterface::class.java, fsModule)
        quickJs.set("_os", PolymathJSOSInterface::class.java, osModule)
        quickJs.set("_process", PolymathJSProcessInterface::class.java, processModule)
        quickJs.set("_http", PolymathJSHttpInterface::class.java, httpModule)
        quickJs.set("_crypto", PolymathJSCryptoInterface::class.java, cryptoModule)
        quickJs.set("_kernel", PolymathJSKernelInterface::class.java, kernelModule)
        quickJs.set("_loader", PolymathModuleLoaderInterface::class.java, moduleLoader)

        // 2. Legacy Polymath bridges for backwards compatibility
        val osNativeImpl = PolymathOSNativeImpl(context, repository, shellHolder, onAlert, onConsoleLog)
        quickJs.set("PolymathOSNative", PolymathOSNativeInterface::class.java, osNativeImpl)

        val fsInterface = object : PolymathFS {
            override fun listDir(path: String): String {
                val nodes = runBlocking { repository.listDir(path) }
                val array = JSONArray()
                nodes.forEach { node ->
                    val obj = JSONObject().apply {
                        put("name", node.name)
                        put("path", node.path)
                        put("size", node.size)
                        put("lastModified", node.lastModified)
                        put("isDirectory", node.isDirectory)
                    }
                    array.put(obj)
                }
                return array.toString()
            }

            override fun copy(srcJson: String, dest: String): Boolean {
                val arr = JSONArray(srcJson)
                val list = (0 until arr.length()).map { arr.getString(it) }
                runBlocking { repository.copy(list, dest).collect {} }
                return true
            }

            override fun move(srcJson: String, dest: String): Boolean {
                val arr = JSONArray(srcJson)
                val list = (0 until arr.length()).map { arr.getString(it) }
                runBlocking { repository.move(list, dest).collect {} }
                return true
            }

            override fun delete(pathsJson: String): Boolean {
                val arr = JSONArray(pathsJson)
                val list = (0 until arr.length()).map { arr.getString(it) }
                return runBlocking { repository.delete(list) }
            }

            override fun mkdir(path: String): Boolean {
                return runBlocking { repository.mkdir(path) }
            }

            override fun rename(oldPath: String, newName: String): Boolean {
                return runBlocking { repository.rename(oldPath, newName) }
            }
        }

        val uiInterface = object : PolymathUI {
            override fun showToast(message: String) {
                mainHandler.post {
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            }
        }

        quickJs.set("PolymathFS", PolymathFS::class.java, fsInterface)
        quickJs.set("PolymathUI", PolymathUI::class.java, uiInterface)

        val selectedFilesJson = if (selectedFiles != null) JSONArray(selectedFiles).toString() else "[]"

        // 3. Inject Full POSIX & Node.js Environment Bootstrap
        val bootstrapScript = buildBootstrapScript(scriptName, workingDir, selectedFilesJson)
        quickJs.evaluate(bootstrapScript)
    }

    private fun buildBootstrapScript(
        scriptName: String,
        workingDir: String,
        selectedFilesJson: String
    ): String {
        return """
            var global = this;
            var window = this;
            var selectedFiles = $selectedFilesJson;
            var __timers = {};
            var __timerCounter = 1;

            // 1. Process Global Object
            var process = {
                env: JSON.parse(_process.getEnv()),
                platform: _os.getPlatform(),
                arch: _os.getArch(),
                pid: _process.getPid(),
                cwd: function() { return _process.getCwd(); },
                chdir: function(p) { return _process.setCwd(String(p)); },
                uptime: function() { return _process.getUptime(); },
                argv: ["polymath", "$scriptName"],
                exit: function(code) {
                    throw new Error("Process exited with code " + (code || 0));
                }
            };

            // 2. Buffer Global Constructor
            function Buffer(data, encoding) {
                if (typeof data === 'number') {
                    this._data = new Array(data).fill(0);
                } else if (Array.isArray(data)) {
                    this._data = data.slice();
                } else if (typeof data === 'string') {
                    if (encoding === 'base64') {
                        var decoded = _crypto.base64Decode(data);
                        this._data = [];
                        for (var i = 0; i < decoded.length; i++) this._data.push(decoded.charCodeAt(i));
                    } else if (encoding === 'hex') {
                        this._data = [];
                        for (var i = 0; i < data.length; i += 2) {
                            this._data.push(parseInt(data.substr(i, 2), 16) || 0);
                        }
                    } else {
                        this._data = [];
                        for (var i = 0; i < data.length; i++) this._data.push(data.charCodeAt(i));
                    }
                } else {
                    this._data = [];
                }
                this.length = this._data.length;
            }

            Buffer.from = function(data, encoding) {
                return new Buffer(data, encoding);
            };

            Buffer.alloc = function(size) {
                return new Buffer(size);
            };

            Buffer.isBuffer = function(obj) {
                return obj instanceof Buffer;
            };

            Buffer.prototype.toString = function(encoding) {
                if (encoding === 'base64') {
                    var str = String.fromCharCode.apply(null, this._data);
                    return _crypto.base64Encode(str);
                } else if (encoding === 'hex') {
                    return this._data.map(function(b) {
                        var h = (b & 0xFF).toString(16);
                        return h.length === 1 ? '0' + h : h;
                    }).join('');
                } else {
                    return String.fromCharCode.apply(null, this._data);
                }
            };

            Buffer.prototype.slice = function(start, end) {
                return new Buffer(this._data.slice(start, end));
            };

            // 3. Path Module
            var path = {
                sep: '/',
                join: function() {
                    var parts = [];
                    for (var i = 0; i < arguments.length; i++) {
                        var part = String(arguments[i]);
                        if (part.length > 0) parts.push(part);
                    }
                    var full = parts.join('/');
                    return path.normalize(full);
                },
                resolve: function() {
                    var resolved = "";
                    for (var i = arguments.length - 1; i >= 0; i--) {
                        var p = String(arguments[i]);
                        if (p.startsWith('/')) {
                            resolved = p + (resolved ? '/' + resolved : '');
                            break;
                        } else {
                            resolved = p + (resolved ? '/' + resolved : '');
                        }
                    }
                    if (!resolved.startsWith('/')) {
                        resolved = process.cwd() + '/' + resolved;
                    }
                    return path.normalize(resolved);
                },
                dirname: function(p) {
                    var parts = path.normalize(p).split('/');
                    parts.pop();
                    return parts.join('/') || '/';
                },
                basename: function(p, ext) {
                    var parts = path.normalize(p).split('/');
                    var name = parts.pop() || "";
                    if (ext && name.endsWith(ext)) {
                        name = name.substring(0, name.length - ext.length);
                    }
                    return name;
                },
                extname: function(p) {
                    var name = path.basename(p);
                    var idx = name.lastIndexOf('.');
                    return idx > 0 ? name.substring(idx) : '';
                },
                normalize: function(p) {
                    var parts = p.split('/');
                    var stack = [];
                    for (var i = 0; i < parts.length; i++) {
                        var part = parts[i];
                        if (part === '' || part === '.') continue;
                        if (part === '..') {
                            if (stack.length > 0) stack.pop();
                        } else {
                            stack.push(part);
                        }
                    }
                    return (p.startsWith('/') ? '/' : '') + stack.join('/');
                },
                isAbsolute: function(p) {
                    return String(p).startsWith('/');
                }
            };

            // 4. POSIX File System Module (fs)
            var fs = {
                readFileSync: function(filePath, options) {
                    var enc = (typeof options === 'string') ? options : (options && options.encoding) ? options.encoding : 'utf-8';
                    var res = _posix.readFileSync(String(filePath), enc);
                    if (enc === 'buffer') {
                        return Buffer.from(res, 'base64');
                    }
                    return res;
                },
                writeFileSync: function(filePath, data) {
                    var content = Buffer.isBuffer(data) ? data.toString('utf-8') : String(data);
                    return _posix.writeFileSync(String(filePath), content);
                },
                appendFileSync: function(filePath, data) {
                    var content = Buffer.isBuffer(data) ? data.toString('utf-8') : String(data);
                    return _posix.appendFileSync(String(filePath), content);
                },
                existsSync: function(filePath) {
                    return _posix.existsSync(String(filePath));
                },
                readdirSync: function(dirPath) {
                    var raw = _posix.readdirSync(String(dirPath));
                    return JSON.parse(raw);
                },
                statSync: function(filePath) {
                    var raw = _posix.statSync(String(filePath));
                    var stat = JSON.parse(raw);
                    stat.isFile = function() { return stat.isFile; };
                    stat.isDirectory = function() { return stat.isDirectory; };
                    return stat;
                },
                mkdirSync: function(dirPath, options) {
                    var recursive = (options && options.recursive) ? true : false;
                    return _posix.mkdirSync(String(dirPath), recursive);
                },
                unlinkSync: function(filePath) {
                    return _posix.unlinkSync(String(filePath));
                },
                rmdirSync: function(dirPath, options) {
                    var recursive = (options && options.recursive) ? true : false;
                    return _posix.rmdirSync(String(dirPath), recursive);
                },
                copyFileSync: function(src, dest) {
                    return _posix.copyFileSync(String(src), String(dest));
                },
                renameSync: function(oldPath, newPath) {
                    return _posix.renameSync(String(oldPath), String(newPath));
                },
                chmodSync: function(filePath, mode) {
                    return _posix.chmodSync(String(filePath), String(mode));
                }
            };

            // 5. OS Module
            var os = {
                arch: function() { return _os.getArch(); },
                platform: function() { return _os.getPlatform(); },
                hostname: function() { return _os.getHostname(); },
                cpus: function() { return JSON.parse(_os.getCpus()); },
                totalmem: function() { return _os.getTotalMem(); },
                freemem: function() { return _os.getFreeMem(); },
                uptime: function() { return _os.getUptime(); },
                loadavg: function() { return JSON.parse(_os.getLoadAvg()); },
                homedir: function() { return _os.getHomeDir(); },
                tmpdir: function() { return _os.getTmpDir(); },
                networkInterfaces: function() { return JSON.parse(_os.getNetworkInterfaces()); }
            };

            // 6. Child Process Module
            var child_process = {
                execSync: function(cmd) {
                    var raw = _process.execSync(String(cmd));
                    var res = JSON.parse(raw);
                    if (!res.success && res.code !== 0) {
                        var err = new Error(res.stderr || ("Command failed: " + cmd));
                        err.status = res.code;
                        err.stdout = res.stdout;
                        err.stderr = res.stderr;
                        throw err;
                    }
                    return res.stdout;
                }
            };

            // 7. Crypto Module
            var crypto = {
                createHash: function(algo) {
                    var currentData = "";
                    return {
                        update: function(data) {
                            currentData += (Buffer.isBuffer(data) ? data.toString('utf-8') : String(data));
                            return this;
                        },
                        digest: function(enc) {
                            var h = _crypto.hash(algo, currentData);
                            if (enc === 'base64') {
                                return _crypto.base64Encode(h);
                            }
                            return h;
                        }
                    };
                },
                randomUUID: function() {
                    return _crypto.randomUuid();
                },
                randomBytes: function(size) {
                    var hex = _crypto.randomHex(size || 16);
                    return Buffer.from(hex, 'hex');
                }
            };

            // 8. Kernel Module
            var kernel = {
                getReport: function() {
                    return JSON.parse(_kernel.getKernelReport());
                },
                readVirtualFile: function(path) {
                    return _kernel.readVirtualFile(String(path));
                },
                setCpuGovernor: function(gov) {
                    return _kernel.setCpuGovernor(String(gov));
                }
            };

            // 9. HTTP Client & Fetch
            var http = {
                request: function(options, callback) {
                    var url = typeof options === 'string' ? options : options.url;
                    var method = (options && options.method) ? options.method : 'GET';
                    var headers = (options && options.headers) ? JSON.stringify(options.headers) : '{}';
                    var body = (options && options.body) ? String(options.body) : '';
                    var raw = _http.fetchSync(url, method, headers, body);
                    var res = JSON.parse(raw);
                    if (callback) callback(res);
                    return res;
                }
            };

            function fetch(url, options) {
                var method = (options && options.method) ? options.method : 'GET';
                var headers = (options && options.headers) ? JSON.stringify(options.headers) : '{}';
                var body = (options && options.body) ? String(options.body) : '';
                var raw = _http.fetchSync(String(url), method, headers, body);
                var res = JSON.parse(raw);
                return {
                    status: res.status,
                    statusText: res.statusText,
                    ok: res.ok,
                    headers: res.headers,
                    text: function() { return res.body; },
                    json: function() { return JSON.parse(res.body); }
                };
            }

            // 10. CommonJS Module System (require)
            var __moduleCache = {};
            function require(id) {
                id = String(id);
                if (id === 'fs') return fs;
                if (id === 'os') return os;
                if (id === 'path') return path;
                if (id === 'child_process') return child_process;
                if (id === 'crypto') return crypto;
                if (id === 'kernel') return kernel;
                if (id === 'http' || id === 'https') return http;

                if (__moduleCache[id]) {
                    return __moduleCache[id].exports;
                }

                var source = _loader.loadModuleSource(id, process.cwd());
                var module = { id: id, exports: {} };
                __moduleCache[id] = module;

                var wrapper = "(function(exports, require, module, __filename, __dirname) {" + source + "\n})";
                var fn = eval(wrapper);
                fn(module.exports, require, module, id, path.dirname(id));
                return module.exports;
            }
            require.cache = __moduleCache;

            // 11. Polymath & PolymathOS Legacy APIs (Full Power Unrestricted)
            var Polymath = {
                fs: PolymathFS,
                ui: PolymathUI
            };

            var PolymathOS = {
                toast: function(msg) { PolymathOSNative.toast(String(msg)); },
                alert: function(arg1, arg2) {
                    if (arg2 !== undefined) {
                        return PolymathOSNative.alert2(String(arg1), String(arg2));
                    } else {
                        return PolymathOSNative.alert2("Polymath Alert", String(arg1));
                    }
                },
                prompt: function(title, callbackName) {
                    var res = PolymathOSNative.prompt2(String(title), callbackName ? String(callbackName) : "");
                    if (callbackName && typeof window[callbackName] === 'function') {
                        try { window[callbackName](res); } catch(e) {}
                    }
                    return res;
                },
                setTheme: function(json) { return PolymathOSNative.setTheme(String(json)); },
                daemonCommand: function(action, payload) { return PolymathOSNative.daemonCommand(String(action), String(payload || "")); },
                listen: function(event, path, cb) { return PolymathOSNative.listen(String(event), String(path), String(cb || "")); },
                readFile: function(p) { return fs.readFileSync(p, 'utf-8'); },
                writeFile: function(p, c) { return fs.writeFileSync(p, c); },
                ftpRequest: function(host, port, user, pass, action, p) {
                    return PolymathOSNative.ftpRequest(String(host), parseInt(port)||21, String(user), String(pass), String(action), String(p));
                },
                smbRequest: function(host, port, user, pass, action, p) {
                    return PolymathOSNative.smbRequest(String(host), parseInt(port)||445, String(user), String(pass), String(action), String(p));
                },
                getDiskStats: function() {
                    var raw = PolymathOSNative.getDiskStats();
                    try { return JSON.parse(raw); } catch(e) { return {}; }
                },
                getKernelStats: function() {
                    var raw = PolymathOSNative.getKernelStats();
                    try { return JSON.parse(raw); } catch(e) { return {}; }
                },
                findFiles: function(dir, ext) {
                    var raw = PolymathOSNative.findFiles(String(dir || "/storage/emulated/0"), String(ext || ""));
                    try { return JSON.parse(raw); } catch(e) { return []; }
                },
                executeShell: function(cmd) {
                    var raw = PolymathOSNative.executeShell(String(cmd));
                    try { return JSON.parse(raw); } catch(e) { return { success: false, output: "", error: e.message }; }
                },
                exists: function(p) {
                    return fs.existsSync(p);
                }
            };

            // 12. Rich Console
            var __timeRecords = {};
            var console = {
                log: function() {
                    var msg = Array.prototype.slice.call(arguments).map(function(a) {
                        return typeof a === 'object' ? JSON.stringify(a) : String(a);
                    }).join(" ");
                    PolymathOSNative.consoleLog("LOG", msg);
                },
                info: function() {
                    var msg = Array.prototype.slice.call(arguments).map(function(a) {
                        return typeof a === 'object' ? JSON.stringify(a) : String(a);
                    }).join(" ");
                    PolymathOSNative.consoleLog("INFO", msg);
                },
                warn: function() {
                    var msg = Array.prototype.slice.call(arguments).map(function(a) {
                        return typeof a === 'object' ? JSON.stringify(a) : String(a);
                    }).join(" ");
                    PolymathOSNative.consoleLog("WARN", msg);
                },
                error: function() {
                    var msg = Array.prototype.slice.call(arguments).map(function(a) {
                        return typeof a === 'object' ? JSON.stringify(a) : String(a);
                    }).join(" ");
                    PolymathOSNative.consoleLog("ERROR", msg);
                },
                time: function(label) {
                    __timeRecords[label || 'default'] = Date.now();
                },
                timeEnd: function(label) {
                    var lbl = label || 'default';
                    var start = __timeRecords[lbl];
                    if (start) {
                        var elapsed = Date.now() - start;
                        console.log(lbl + ": " + elapsed + "ms");
                        delete __timeRecords[lbl];
                    }
                }
            };
        """.trimIndent()
    }
}
