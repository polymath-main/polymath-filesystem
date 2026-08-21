package com.polymath.fs;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Button;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.Toast;
import android.view.inputmethod.EditorInfo;
import android.view.KeyEvent;
import android.app.AlertDialog;
import android.graphics.drawable.GradientDrawable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.polymath.fs.core.ConfigManager;
import com.polymath.fs.ui.FileAdapter;
import com.polymath.fs.ui.ScriptAdapter;
import com.polymath.fs.core.JsRuntimeManager;
import com.polymath.fs.viewers.EditorActivity;
import com.polymath.fs.viewers.ImageViewerActivity;
import com.polymath.fs.viewers.MediaPlayerActivity;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

public class MainActivity extends Activity {

    // Views
    private View mainContainer, dashboardContainer, eyeStrainOverlay;
    private TextView navFileExplorer, navJsEngine, btnNewScript;
    private TextView pathText;
    private EditText searchInput;
    private RecyclerView fileRecyclerView, scriptRecyclerView;
    private LinearLayout tabsContainer, bookmarksContainer;
    
    // State
    private FileAdapter adapter;
    private ScriptAdapter scriptAdapter;
    private File currentDir;
    private List<File> currentFiles = new ArrayList<>();
    private List<File> extensionScripts = new ArrayList<>();
    private ConfigManager configManager;
    private Set<String> bookmarkedScripts = new HashSet<>();
    
    private List<File> tabs = new ArrayList<>();
    private int activeTabIndex = 0;
    private boolean isEyeStrainEnabled = false;
    private final File extensionsDir = new File(Environment.getExternalStorageDirectory(), "PolymathExtensions");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        configManager = ConfigManager.getInstance(this);

        // Init views
        mainContainer = findViewById(R.id.mainContainer);
        dashboardContainer = findViewById(R.id.dashboardContainer);
        eyeStrainOverlay = findViewById(R.id.eyeStrainOverlay);
        navFileExplorer = findViewById(R.id.navFileExplorer);
        navJsEngine = findViewById(R.id.navJsEngine);
        btnNewScript = findViewById(R.id.btnNewScript);
        pathText = findViewById(R.id.pathText);
        searchInput = findViewById(R.id.searchInput);
        fileRecyclerView = findViewById(R.id.fileRecyclerView);
        scriptRecyclerView = findViewById(R.id.scriptRecyclerView);
        tabsContainer = findViewById(R.id.tabsContainer);
        bookmarksContainer = findViewById(R.id.bookmarksContainer);
        
        fileRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        scriptRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        setupNavigation();
        applyTheme();

        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                String query = searchInput.getText().toString();
                if (!query.isEmpty()) fetchFromDaemonSearch(currentDir.getAbsolutePath(), query);
                else loadDirectory(currentDir);
                return true;
            }
            return false;
        });
        
        pathText.setOnLongClickListener(v -> {
            isEyeStrainEnabled = !isEyeStrainEnabled;
            eyeStrainOverlay.setVisibility(isEyeStrainEnabled ? View.VISIBLE : View.GONE);
            return true;
        });
        
        btnNewScript.setOnClickListener(v -> createNewScriptWorkspace());

        if (!extensionsDir.exists()) {
            extensionsDir.mkdirs();
            extractBuiltInExtensions();
        }

        tabs.add(Environment.getExternalStorageDirectory());
        renderTabs();
        loadDirectory(tabs.get(activeTabIndex));
        loadScripts();
    }

    private void extractBuiltInExtensions() {
        try {
            String[] assets = getAssets().list("extensions");
            if (assets == null) return;
            for (String scriptDir : assets) {
                File targetDir = new File(extensionsDir, scriptDir);
                targetDir.mkdirs();
                
                String[] files = getAssets().list("extensions/" + scriptDir);
                if (files == null) continue;
                for (String file : files) {
                    InputStream in = getAssets().open("extensions/" + scriptDir + "/" + file);
                    OutputStream out = new java.io.FileOutputStream(new File(targetDir, file));
                    byte[] buffer = new byte[1024];
                    int read;
                    while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
                    in.close();
                    out.close();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setupNavigation() {
        navFileExplorer.setOnClickListener(v -> {
            mainContainer.setVisibility(View.VISIBLE);
            dashboardContainer.setVisibility(View.GONE);
            navFileExplorer.setTextColor(Color.parseColor("#f8fafc"));
            navJsEngine.setTextColor(Color.parseColor("#64748b"));
        });
        
        navJsEngine.setOnClickListener(v -> {
            mainContainer.setVisibility(View.GONE);
            dashboardContainer.setVisibility(View.VISIBLE);
            navFileExplorer.setTextColor(Color.parseColor("#64748b"));
            navJsEngine.setTextColor(Color.parseColor("#f8fafc"));
            loadScripts();
        });
    }

    // --- Script Engine Dashboard ---

    private void loadScripts() {
        extensionScripts.clear();
        if (extensionsDir.exists()) {
            File[] files = extensionsDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isDirectory()) {
                        File index = new File(f, "index.js");
                        if (index.exists()) extensionScripts.add(index);
                    } else if (f.getName().endsWith(".js")) {
                        extensionScripts.add(f);
                    }
                }
            }
        }
        
        scriptAdapter = new ScriptAdapter(extensionScripts, new ScriptAdapter.OnScriptClickListener() {
            @Override public void onRun(File script) {
                JsRuntimeManager.executeScript(MainActivity.this, script);
            }
            @Override public void onEdit(File script) {
                openViewer(script);
            }
            @Override public void onBookmark(File script) {
                if (bookmarkedScripts.contains(script.getAbsolutePath())) {
                    bookmarkedScripts.remove(script.getAbsolutePath());
                    Toast.makeText(MainActivity.this, "Bookmark Removed", Toast.LENGTH_SHORT).show();
                } else {
                    bookmarkedScripts.add(script.getAbsolutePath());
                    Toast.makeText(MainActivity.this, "Bookmarked!", Toast.LENGTH_SHORT).show();
                }
                renderBookmarks();
            }
        });
        scriptRecyclerView.setAdapter(scriptAdapter);
    }

    private void createNewScriptWorkspace() {
        final EditText input = new EditText(this);
        input.setHint("e.g. ImageOptimizer");
        new AlertDialog.Builder(this)
            .setTitle("New Extension Workspace")
            .setView(input)
            .setPositiveButton("Create", (dialog, which) -> {
                String name = input.getText().toString().trim();
                if (!name.isEmpty()) {
                    File workspace = new File(extensionsDir, name);
                    workspace.mkdirs();
                    File index = new File(workspace, "index.js");
                    try {
                        index.createNewFile();
                        loadScripts();
                        openViewer(index);
                    } catch (Exception e) {
                        Toast.makeText(this, "Failed", Toast.LENGTH_SHORT).show();
                    }
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void renderBookmarks() {
        bookmarksContainer.removeAllViews();
        for (String path : bookmarkedScripts) {
            File script = new File(path);
            TextView btn = new TextView(this);
            btn.setText("⭐ " + (script.getName().equals("index.js") ? script.getParentFile().getName() : script.getName()));
            btn.setTextColor(Color.parseColor("#fbbf24"));
            btn.setPadding(24, 12, 24, 12);
            GradientDrawable gd = new GradientDrawable();
            gd.setColor(Color.parseColor("#1e293b"));
            gd.setCornerRadius(16f);
            btn.setBackground(gd);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.setMargins(0, 0, 16, 0);
            btn.setLayoutParams(params);
            
            btn.setOnClickListener(v -> {
                Toast.makeText(this, "Running " + script.getName() + " on " + currentDir.getName(), Toast.LENGTH_SHORT).show();
                JsRuntimeManager.executeScript(MainActivity.this, script);
            });
            bookmarksContainer.addView(btn);
        }
        bookmarksContainer.setVisibility(bookmarkedScripts.isEmpty() ? View.GONE : View.VISIBLE);
    }

    // --- File Explorer ---

    private void renderTabs() {
        tabsContainer.removeAllViews();
        for (int i = 0; i < tabs.size(); i++) {
            final int index = i;
            Button tabBtn = new Button(this);
            tabBtn.setText(tabs.get(i).getName().isEmpty() ? "Root" : tabs.get(i).getName());
            tabBtn.setAllCaps(false);
            tabBtn.setTextColor(index == activeTabIndex ? Color.WHITE : Color.LTGRAY);
            
            GradientDrawable gd = new GradientDrawable();
            gd.setColor(index == activeTabIndex ? Color.parseColor("#3b82f6") : Color.parseColor("#334155"));
            gd.setCornerRadius(16f);
            gd.setStroke(2, index == activeTabIndex ? Color.parseColor("#60a5fa") : Color.TRANSPARENT);
            tabBtn.setBackground(gd);
            
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, 100);
            params.setMargins(0, 0, 16, 0);
            tabBtn.setLayoutParams(params);
            
            tabBtn.setOnClickListener(v -> {
                activeTabIndex = index;
                renderTabs();
                loadDirectory(tabs.get(activeTabIndex));
            });
            
            tabBtn.setOnLongClickListener(v -> {
                if (tabs.size() > 1) {
                    tabs.remove(index);
                    if (activeTabIndex >= tabs.size()) activeTabIndex = tabs.size() - 1;
                    renderTabs();
                    loadDirectory(tabs.get(activeTabIndex));
                }
                return true;
            });
            tabsContainer.addView(tabBtn);
        }
        
        Button newTabBtn = new Button(this);
        newTabBtn.setText("+");
        newTabBtn.setTextColor(Color.WHITE);
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.parseColor("#334155"));
        gd.setCornerRadius(16f);
        newTabBtn.setBackground(gd);
        newTabBtn.setLayoutParams(new LinearLayout.LayoutParams(100, 100));
        newTabBtn.setOnClickListener(v -> {
            tabs.add(Environment.getExternalStorageDirectory());
            activeTabIndex = tabs.size() - 1;
            renderTabs();
            loadDirectory(tabs.get(activeTabIndex));
        });
        tabsContainer.addView(newTabBtn);
    }

    private void applyTheme() {
        try {
            JSONObject theme = configManager.getConfig().getJSONObject("theme");
            findViewById(R.id.mainContainer).setBackgroundColor(Color.parseColor(theme.getString("primaryBg")));
            findViewById(R.id.headerContainer).setBackgroundColor(Color.parseColor(theme.getString("secondaryBg")));
            pathText.setTextColor(Color.parseColor(theme.getString("textColor")));
        } catch (Exception ignored) {}
    }

    private void loadDirectory(File dir) {
        currentDir = dir;
        tabs.set(activeTabIndex, dir);
        renderTabs();
        pathText.setText(dir.getAbsolutePath());

        if (!dir.canRead()) {
            fetchFromDaemon(dir.getAbsolutePath());
            return;
        }

        File[] files = dir.listFiles();
        currentFiles = new ArrayList<>();
        if (files != null) {
            Arrays.sort(files, (f1, f2) -> {
                if (f1.isDirectory() && !f2.isDirectory()) return -1;
                if (!f1.isDirectory() && f2.isDirectory()) return 1;
                return f1.getName().compareToIgnoreCase(f2.getName());
            });
            currentFiles.addAll(Arrays.asList(files));
        }
        updateRecyclerView();
    }

    private void fetchFromDaemon(String path) {
        new Thread(() -> {
            try {
                JSONObject req = new JSONObject();
                req.put("action", "list_dir");
                req.put("path", path);
                
                JSONObject res = com.polymath.fs.core.RootEngine.executeAction(req);
                if (res.optBoolean("success")) {
                    JSONArray filesArr = res.optJSONArray("files");
                    if (filesArr != null) {
                        currentFiles = new ArrayList<>();
                        for (int i = 0; i < filesArr.length(); i++) {
                            currentFiles.add(new File(filesArr.getJSONObject(i).getString("uri")));
                        }
                        runOnUiThread(this::updateRecyclerView);
                    }
                }
            } catch (Exception e) {}
        }).start();
    }

    private void fetchFromDaemonSearch(String path, String query) {
        new Thread(() -> {
            try {
                JSONObject req = new JSONObject();
                req.put("action", "search_files");
                req.put("path", path);
                req.put("query", query);
                
                JSONObject res = com.polymath.fs.core.RootEngine.executeAction(req);
                if (res.optBoolean("success")) {
                    JSONArray filesArr = res.optJSONArray("files");
                    if (filesArr != null) {
                        currentFiles = new ArrayList<>();
                        for (int i = 0; i < filesArr.length(); i++) {
                            currentFiles.add(new File(filesArr.getJSONObject(i).getString("uri")));
                        }
                        runOnUiThread(this::updateRecyclerView);
                    }
                }
            } catch (Exception e) {}
        }).start();
    }

    private void updateRecyclerView() {
        adapter = new FileAdapter(currentFiles, new FileAdapter.OnItemClickListener() {
            @Override public void onItemClick(File file) {
                if (file.isDirectory()) loadDirectory(file);
                else openViewer(file);
            }
            @Override public void onItemLongClick(File file) {
                showAdvancedFeaturesMenu(file);
            }
        });
        adapter.setConfig(configManager.getConfig());
        fileRecyclerView.setAdapter(adapter);
    }

    private void showAdvancedFeaturesMenu(File file) {
        String[] options = {
            "Delete File (Daemon)", "Archive (Daemon)", "Format Cloaking (Toggle File Scramble)",
            "Hardlink Deduplication (Zero-Space)", "Chronos (Time-Travel Snapshot)",
            "Ghost Vault (Forensic Shredder)", "Mount RAM-Disk (HyperDrive)", "Restore Chronos Snapshot",
            "Execute JS Extension (V8 WebKit Engine)"
        };

        new AlertDialog.Builder(this)
            .setTitle("Polymath Core Operations")
            .setItems(options, (dialog, which) -> {
                String action = "";
                switch (which) {
                    case 0: action = "delete_file"; break;
                    case 1: action = "archive"; break;
                    case 2: action = "format_cloak"; break;
                    case 3: action = "hardlink_dedup"; break;
                    case 4: action = "chronos_snapshot"; break;
                    case 5: action = "ghost_vault"; break;
                    case 6: action = "mount_ramdisk"; break;
                    case 7: action = "chronos_restore"; break;
                    case 8: 
                        JsRuntimeManager.executeScript(MainActivity.this, file);
                        return;
                }
                executeAdvancedAction(action, file.getAbsolutePath());
            })
            .show();
    }

    private void executeAdvancedAction(String action, String path) {
        new Thread(() -> {
            try {
                JSONObject req = new JSONObject();
                req.put("action", action);
                req.put("path", path);
                if (action.equals("chronos_restore")) req.put("archive", path + "/.chronos_1.tar.gz");
                if (action.equals("archive")) {
                    req.put("action", "execute_command");
                    req.put("command", "tar -czf '" + path + ".tar.gz' -C '" + new File(path).getParent() + "' '" + new File(path).getName() + "'");
                }
                
                com.polymath.fs.core.RootEngine.executeAction(req);
                
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Execution Complete: " + action, Toast.LENGTH_LONG).show();
                    loadDirectory(currentDir);
                });
            } catch (Exception e) {}
        }).start();
    }

    private void openViewer(File file) {
        String name = file.getName().toLowerCase();
        Intent intent;
        if (name.endsWith(".jpg") || name.endsWith(".png") || name.endsWith(".jpeg") || name.endsWith(".gif")) intent = new Intent(this, ImageViewerActivity.class);
        else if (name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".mp3") || name.endsWith(".wav")) intent = new Intent(this, MediaPlayerActivity.class);
        else if (name.endsWith(".txt") || name.endsWith(".md") || name.endsWith(".java") || name.endsWith(".js") || name.endsWith(".xml") || name.endsWith(".json")) intent = new Intent(this, EditorActivity.class);
        else { Toast.makeText(this, "No built-in viewer for this type.", Toast.LENGTH_SHORT).show(); return; }
        intent.putExtra("filePath", file.getAbsolutePath());
        startActivity(intent);
    }

    @Override
    public void onBackPressed() {
        if (dashboardContainer.getVisibility() == View.VISIBLE) {
            navFileExplorer.performClick();
        } else if (!currentDir.getAbsolutePath().equals(Environment.getExternalStorageDirectory().getAbsolutePath()) && !currentDir.getAbsolutePath().equals("/")) {
            loadDirectory(currentDir.getParentFile());
        } else {
            super.onBackPressed();
        }
    }
}
