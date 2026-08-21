const net = require('net');
const fs = require('fs');
const path = require('path');
const vm = require('vm');
const readline = require('readline');

// PolymathOS Native Bridge Mock for Node.js
class PolymathBridge {
    constructor(baseDir) {
        this.baseDir = baseDir;
    }

    toast(message) {
        console.log(`\n[UI TOAST] 🍞 ${message}`);
    }

    alert(title, message) {
        console.log(`\n[UI ALERT] ⚠️ ${title}`);
        console.log(`---------------------------------`);
        console.log(message);
        console.log(`---------------------------------\n`);
    }

    prompt(title, callbackName) {
        console.log(`\n[UI PROMPT] ❓ ${title}`);
        const rl = readline.createInterface({
            input: process.stdin,
            output: process.stdout
        });
        
        rl.question('> ', (answer) => {
            rl.close();
            // Simulate the WebView evaluateJavascript callback execution
            try {
                // The global scope is where the callback function is defined
                if (typeof global[callbackName] === 'function') {
                    global[callbackName](answer);
                } else {
                    console.error(`[ERROR] Callback function '${callbackName}' not found in global scope.`);
                }
            } catch (e) {
                console.error(`[ERROR] Executing callback: ${e.message}`);
            }
        });
    }

    daemonCommand(action, payloadPath) {
        console.log(`[DAEMON IPC] Sending action '${action}' for path '${payloadPath}'`);
        try {
            const execSync = require('child_process').execSync;
            const req = JSON.stringify({ action: action, path: payloadPath, command: payloadPath });
            fs.writeFileSync('temp_req.json', req);
            const res = execSync(`cat temp_req.json | nc 127.0.0.1 50505 -w 2`);
            return res.toString();
        } catch (e) {
            return JSON.stringify({ success: false, error: e.message });
        }
    }

    readFile(filePath) {
        try {
            return fs.readFileSync(filePath, 'utf8');
        } catch (e) {
            return "";
        }
    }

    require(relativePath) {
        try {
            const target = path.join(this.baseDir, relativePath);
            return fs.readFileSync(target, 'utf8');
        } catch (e) {
            console.error(`Module not found: ${relativePath}`);
            return "";
        }
    }
}

async function runSandbox(scriptPath) {
    console.log(`\n=== 🧪 STARTING CUSTOM SANDBOX PLAYGROUND ===`);
    console.log(`Loading script: ${scriptPath}`);
    
    const scriptContent = fs.readFileSync(scriptPath, 'utf8');
    const baseDir = path.dirname(scriptPath);
    
    // Instantiate Bridge
    const bridge = new PolymathBridge(baseDir);
    
    // Inject the bridge into the Node.js global object
    global.PolymathOS = bridge;

    // The polyfill used in Java JsRuntimeManager
    const polyfill = `
        window = global;
        window.require = function(relativePath) {
            var code = PolymathOS.require(relativePath);
            var module = { exports: {} };
            var fn = new Function('module', 'exports', code);
            fn(module, module.exports);
            return module.exports;
        };
    `;

    // Execute script
    try {
        const script = new vm.Script(polyfill + scriptContent);
        const result = script.runInThisContext();
        if (result !== undefined) {
            console.log(`\n[V8 ENGINE RETURN] => ${result}`);
        }
    } catch (e) {
        console.error(`[ENGINE CRASH] ${e.message}`);
    }
}

const target = process.argv[2];
if (!target) {
    console.log("Usage: node test_sandbox.js <path_to_index.js>");
    process.exit(1);
}

runSandbox(target);
