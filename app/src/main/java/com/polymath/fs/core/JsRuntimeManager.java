package com.polymath.fs.core;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Toast;

import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import org.json.JSONObject;

public class JsRuntimeManager {
    
    // The Bridge exposed to JavaScript via V8 WebKit Engine
    public static class PolymathBridge {
        private final Context context;
        private final String baseDir;

        public PolymathBridge(Context context, String baseDir) {
            this.context = context;
            this.baseDir = baseDir;
        }

        // Exposed to JS: PolymathOS.toast("Hello")
        @JavascriptInterface
        public void toast(String message) {
            new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show());
        }

        // Exposed to JS: PolymathOS.daemonCommand("archive", "/sdcard/test.txt")
        @JavascriptInterface
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
        @JavascriptInterface
        public String readFile(String path) {
            try {
                java.util.Scanner scanner = new java.util.Scanner(new File(path)).useDelimiter("\\A");
                return scanner.hasNext() ? scanner.next() : "";
            } catch (Exception e) {
                return "";
            }
        }
        
        // Exposed to JS: var utils = PolymathOS.require("utils.js")
        @JavascriptInterface
        public String require(String relativePath) {
            try {
                File target = new File(baseDir, relativePath);
                if (!target.exists()) return "console.error('Module not found: " + target.getAbsolutePath() + "');";
                
                java.util.Scanner scanner = new java.util.Scanner(target).useDelimiter("\\A");
                return scanner.hasNext() ? scanner.next() : "";
            } catch (Exception e) {
                return "console.error('Require Error: " + e.getMessage() + "');";
            }
        }
    }

    public static void executeScript(Context androidContext, File scriptFile) {
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                java.util.Scanner scanner = new java.util.Scanner(scriptFile).useDelimiter("\\A");
                String scriptContent = scanner.hasNext() ? scanner.next() : "";
                
                // Inject custom require polyfill to execute the returned string immediately
                String polyfill = "window.require = function(path) { " +
                                  "  var code = PolymathOS.require(path);" +
                                  "  var module = { exports: {} };" +
                                  "  var fn = new Function('module', 'exports', code);" +
                                  "  fn(module, module.exports);" +
                                  "  return module.exports;" +
                                  "};\n";

                WebView webView = new WebView(androidContext);
                webView.getSettings().setJavaScriptEnabled(true);
                webView.addJavascriptInterface(new PolymathBridge(androidContext, scriptFile.getParent()), "PolymathOS");
                
                webView.evaluateJavascript(polyfill + scriptContent, result -> {
                    if (result != null && !result.equals("null")) {
                        Toast.makeText(androidContext, "JS Result: " + result, Toast.LENGTH_LONG).show();
                    }
                });

            } catch (Exception e) {
                Toast.makeText(androidContext, "JS Engine Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }
}
