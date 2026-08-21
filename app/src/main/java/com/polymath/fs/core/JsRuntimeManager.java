package com.polymath.fs.core;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Toast;

import java.io.File;
import org.json.JSONObject;

public class JsRuntimeManager {
    
    // The Bridge exposed to JavaScript via V8 WebKit Engine
    public static class PolymathBridge {
        private final Context context;
        private final String baseDir;
        private WebView webView;

        public PolymathBridge(Context context, String baseDir) {
            this.context = context;
            this.baseDir = baseDir;
        }

        public void attachWebView(WebView wv) {
            this.webView = wv;
        }

        // Exposed to JS: PolymathOS.toast("Hello")
        @JavascriptInterface
        public void toast(String message) {
            new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show());
        }

        // Exposed to JS: PolymathOS.alert("Title", "Message")
        @JavascriptInterface
        public void alert(String title, String message) {
            new Handler(Looper.getMainLooper()).post(() -> {
                new android.app.AlertDialog.Builder(context)
                    .setTitle(title)
                    .setMessage(message)
                    .setPositiveButton("OK", null)
                    .show();
            });
        }

        // Exposed to JS: PolymathOS.prompt("Enter Password", "callbackFnName")
        @JavascriptInterface
        public void prompt(String title, String callbackName) {
            new Handler(Looper.getMainLooper()).post(() -> {
                final android.widget.EditText input = new android.widget.EditText(context);
                new android.app.AlertDialog.Builder(context)
                    .setTitle(title)
                    .setView(input)
                    .setPositiveButton("Submit", (dialog, which) -> {
                        String txt = input.getText().toString().replace("'", "\\'");
                        if (webView != null) webView.evaluateJavascript(callbackName + "('" + txt + "');", null);
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            });
        }

        // Exposed to JS: PolymathOS.daemonCommand("archive", "/sdcard/test.txt")
        @JavascriptInterface
        public String daemonCommand(String action, String path) {
            try {
                JSONObject req = new JSONObject();
                req.put("action", action);
                req.put("path", path);
                JSONObject res = RootEngine.executeAction(req);
                return res.toString();
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

        @JavascriptInterface
        public void setTheme(String jsonConfig) {
            new Handler(Looper.getMainLooper()).post(() -> {
                try {
                    JSONObject config = new JSONObject(jsonConfig);
                    android.content.SharedPreferences prefs = context.getSharedPreferences("pfs_prefs", Context.MODE_PRIVATE);
                    prefs.edit().putString("config_json", config.toString()).apply();
                    if (context instanceof com.polymath.fs.MainActivity) {
                        ((com.polymath.fs.MainActivity) context).applyTheme();
                        Toast.makeText(context, "Theme Applied by JS Modular", Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    Toast.makeText(context, "Theme Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        }

        @JavascriptInterface
        public void elevate(String mode) {
            new Handler(Looper.getMainLooper()).post(() -> {
                if (mode.equals("root")) {
                    RootEngine.executeCommand("su -c echo 'Root Granted'");
                    Toast.makeText(context, "Elevated to Root via JS", Toast.LENGTH_SHORT).show();
                } else if (mode.equals("shizuku")) {
                    // Placeholder for Shizuku IPC binding
                    Toast.makeText(context, "Elevated to Shizuku via JS", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @JavascriptInterface
        public void listen(String eventType, String path, String callbackName) {
            if (eventType.equals("FILE_CREATED")) {
                new Thread(() -> {
                    android.os.FileObserver observer = new android.os.FileObserver(path, android.os.FileObserver.CREATE) {
                        @Override
                        public void onEvent(int event, String file) {
                            if (file != null) {
                                new Handler(Looper.getMainLooper()).post(() -> {
                                    if (webView != null) webView.evaluateJavascript(callbackName + "('" + file + "');", null);
                                });
                            }
                        }
                    };
                    observer.startWatching();
                }).start();
                new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(context, "JS Automation Listener attached to " + path, Toast.LENGTH_SHORT).show());
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
                PolymathBridge bridge = new PolymathBridge(androidContext, scriptFile.getParent());
                bridge.attachWebView(webView);
                
                webView.getSettings().setJavaScriptEnabled(true);
                webView.addJavascriptInterface(bridge, "PolymathOS");
                
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
