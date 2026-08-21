package com.polymath.fs.core;

import android.content.Context;
import android.widget.Toast;

import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import org.json.JSONObject;

public class JsRuntimeManager {
    
    // The Bridge exposed to JavaScript
    public static class PolymathBridge {
        private final Context context;
        private final org.mozilla.javascript.Context rhinoContext;
        private final Scriptable scope;
        private final String baseDir;

        public PolymathBridge(Context context, org.mozilla.javascript.Context rhinoContext, Scriptable scope, String baseDir) {
            this.context = context;
            this.rhinoContext = rhinoContext;
            this.scope = scope;
            this.baseDir = baseDir;
        }

        // Exposed to JS: PolymathOS.toast("Hello")
        public void toast(String message) {
            android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
            handler.post(() -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show());
        }

        // Exposed to JS: PolymathOS.daemonCommand("archive", "/sdcard/test.txt")
        public String daemonCommand(String action, String path) {
            try (Socket socket = new Socket("127.0.0.1", 50505)) {
                OutputStream os = socket.getOutputStream();
                JSONObject req = new JSONObject();
                req.put("action", action);
                req.put("path", path);
                os.write(req.toString().getBytes());
                os.flush();

                InputStream is = socket.getInputStream();
                byte[] buffer = new byte[8192];
                int read = is.read(buffer);
                return new String(buffer, 0, read);
            } catch (Exception e) {
                return "{\"success\":false, \"error\":\"" + e.getMessage() + "\"}";
            }
        }
        
        // Exposed to JS: PolymathOS.readFile("/sdcard/test.txt")
        public String readFile(String path) {
            try {
                java.util.Scanner scanner = new java.util.Scanner(new File(path)).useDelimiter("\\A");
                return scanner.hasNext() ? scanner.next() : "";
            } catch (Exception e) {
                return "";
            }
        }
        
        // Exposed to JS: var utils = PolymathOS.require("utils.js")
        public Object require(String relativePath) {
            try {
                File target = new File(baseDir, relativePath);
                if (!target.exists()) throw new Exception("Module not found: " + target.getAbsolutePath());
                FileReader reader = new FileReader(target);
                return rhinoContext.evaluateReader(scope, reader, target.getName(), 1, null);
            } catch (Exception e) {
                return "Error: " + e.getMessage();
            }
        }
    }

    public static String executeScript(Context androidContext, File scriptFile) {
        org.mozilla.javascript.Context rhino = org.mozilla.javascript.Context.enter();
        rhino.setOptimizationLevel(-1); // Required for Android Dalvik/ART
        try {
            Scriptable scope = rhino.initStandardObjects();
            
            // Inject PolymathBridge into the JS global scope
            PolymathBridge bridge = new PolymathBridge(androidContext, rhino, scope, scriptFile.getParent());
            Object wrappedOut = org.mozilla.javascript.Context.javaToJS(bridge, scope);
            ScriptableObject.putProperty(scope, "PolymathOS", wrappedOut);
            
            FileReader reader = new FileReader(scriptFile);
            Object result = rhino.evaluateReader(scope, reader, scriptFile.getName(), 1, null);
            return org.mozilla.javascript.Context.toString(result);
        } catch (Exception e) {
            return "JS Runtime Error: " + e.getMessage();
        } finally {
            org.mozilla.javascript.Context.exit();
        }
    }
}
