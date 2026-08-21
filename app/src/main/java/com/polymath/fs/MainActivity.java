package com.polymath.fs;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.view.Gravity;
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
import com.polymath.fs.core.JsRuntimeManager;
import com.polymath.fs.viewers.EditorActivity;
import com.polymath.fs.viewers.ImageViewerActivity;
import com.polymath.fs.viewers.MediaPlayerActivity;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends Activity {

    private TextView pathText;
    private EditText searchInput;
    private RecyclerView fileRecyclerView;
    private LinearLayout tabsContainer;
    private View eyeStrainOverlay;
    
    private FileAdapter adapter;
    private File currentDir;
    private List<File> currentFiles;
    private ConfigManager configManager;
    
    private List<File> tabs = new ArrayList<>();
    private int activeTabIndex = 0;
    private boolean isEyeStrainEnabled = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        configManager = ConfigManager.getInstance(this);

        pathText = findViewById(R.id.pathText);
        searchInput = findViewById(R.id.searchInput);
        fileRecyclerView = findViewById(R.id.fileRecyclerView);
        tabsContainer = findViewById(R.id.tabsContainer);
        eyeStrainOverlay = findViewById(R.id.eyeStrainOverlay);
        
        fileRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        applyTheme();

        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                String query = searchInput.getText().toString();
                if (!query.isEmpty()) {
                    fetchFromDaemonSearch(currentDir.getAbsolutePath(), query);
                } else {
                    loadDirectory(currentDir);
                }
                return true;
            }
            return false;
        });
        
        // Add eye strain toggle to path text long click
        pathText.setOnLongClickListener(v -> {
            isEyeStrainEnabled = !isEyeStrainEnabled;
            eyeStrainOverlay.setVisibility(isEyeStrainEnabled ? View.VISIBLE : View.GONE);
            Toast.makeText(this, "Eye Strain Mode: " + (isEyeStrainEnabled ? "ON" : "OFF"), Toast.LENGTH_SHORT).show();
            return true;
        });

        // Initialize first tab
        tabs.add(Environment.getExternalStorageDirectory());
        renderTabs();
        loadDirectory(tabs.get(activeTabIndex));
    }

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
            
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                100
            );
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
        
        // Add New Tab Button
        Button newTabBtn = new Button(this);
        newTabBtn.setText("+");
        newTabBtn.setTextColor(Color.WHITE);
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.parseColor("#334155"));
        gd.setCornerRadius(16f);
        newTabBtn.setBackground(gd);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(100, 100);
        newTabBtn.setLayoutParams(params);
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
            Toast.makeText(this, "Standard API Denied. Requesting Daemon IPC...", Toast.LENGTH_SHORT).show();
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
            try (Socket socket = new Socket("127.0.0.1", 50505)) {
                OutputStream os = socket.getOutputStream();
                JSONObject req = new JSONObject();
                req.put("action", "list_dir");
                req.put("path", path);
                os.write(req.toString().getBytes());
                os.flush();

                InputStream is = socket.getInputStream();
                byte[] buffer = new byte[8192];
                int read = is.read(buffer);
                String response = new String(buffer, 0, read);
                
                JSONObject res = new JSONObject(response);
                if (res.getBoolean("success")) {
                    JSONArray filesArr = res.getJSONArray("files");
                    currentFiles = new ArrayList<>();
                    for (int i = 0; i < filesArr.length(); i++) {
                        JSONObject fObj = filesArr.getJSONObject(i);
                        currentFiles.add(new File(fObj.getString("uri")));
                    }
                    runOnUiThread(this::updateRecyclerView);
                }
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Daemon Unreachable", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void fetchFromDaemonSearch(String path, String query) {
        new Thread(() -> {
            try (Socket socket = new Socket("127.0.0.1", 50505)) {
                OutputStream os = socket.getOutputStream();
                JSONObject req = new JSONObject();
                req.put("action", "search_files");
                req.put("path", path);
                req.put("query", query);
                os.write(req.toString().getBytes());
                os.flush();

                InputStream is = socket.getInputStream();
                byte[] buffer = new byte[16384];
                int read = is.read(buffer);
                String response = new String(buffer, 0, read);
                
                JSONObject res = new JSONObject(response);
                if (res.getBoolean("success")) {
                    JSONArray filesArr = res.getJSONArray("files");
                    currentFiles = new ArrayList<>();
                    for (int i = 0; i < filesArr.length(); i++) {
                        JSONObject fObj = filesArr.getJSONObject(i);
                        currentFiles.add(new File(fObj.getString("uri")));
                    }
                    runOnUiThread(this::updateRecyclerView);
                }
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Search Daemon Unreachable", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void updateRecyclerView() {
        adapter = new FileAdapter(currentFiles, new FileAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(File file) {
                if (file.isDirectory()) {
                    loadDirectory(file);
                } else {
                    openViewer(file);
                }
            }
            @Override
            public void onItemLongClick(File file) {
                showAdvancedFeaturesMenu(file);
            }
        });
        adapter.setConfig(configManager.getConfig());
        fileRecyclerView.setAdapter(adapter);
    }

    private void showAdvancedFeaturesMenu(File file) {
        String[] options = {
            "Delete File (Daemon)",
            "Archive (Daemon)",
            "Format Cloaking (Toggle File Scramble)",
            "Hardlink Deduplication (Zero-Space)",
            "Chronos (Time-Travel Snapshot)",
            "Ghost Vault (Forensic Shredder)",
            "Mount RAM-Disk (HyperDrive)",
            "Restore Chronos Snapshot",
            "Execute JS Extension (Rhino Runtime)"
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
                        String result = JsRuntimeManager.executeScript(MainActivity.this, file);
                        Toast.makeText(MainActivity.this, "JS Result: " + result, Toast.LENGTH_LONG).show();
                        return;
                }
                executeAdvancedAction(action, file.getAbsolutePath());
            })
            .show();
    }

    private void executeAdvancedAction(String action, String path) {
        new Thread(() -> {
            try (Socket socket = new Socket("127.0.0.1", 50505)) {
                OutputStream os = socket.getOutputStream();
                JSONObject req = new JSONObject();
                req.put("action", action);
                req.put("path", path);
                if (action.equals("chronos_restore")) req.put("archive", path + "/.chronos_1.tar.gz");
                if (action.equals("archive")) {
                    req.put("action", "execute_command");
                    req.put("command", "tar -czf '" + path + ".tar.gz' -C '" + new File(path).getParent() + "' '" + new File(path).getName() + "'");
                }
                
                os.write(req.toString().getBytes());
                os.flush();

                InputStream is = socket.getInputStream();
                byte[] buffer = new byte[8192];
                int read = is.read(buffer);
                String response = new String(buffer, 0, read);
                JSONObject res = new JSONObject(response);
                
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Execution Complete: " + action, Toast.LENGTH_LONG).show();
                    loadDirectory(currentDir);
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Daemon Unreachable", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void openViewer(File file) {
        String name = file.getName().toLowerCase();
        Intent intent;
        if (name.endsWith(".jpg") || name.endsWith(".png") || name.endsWith(".jpeg") || name.endsWith(".gif")) {
            intent = new Intent(this, ImageViewerActivity.class);
        } else if (name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".mp3") || name.endsWith(".wav")) {
            intent = new Intent(this, MediaPlayerActivity.class);
        } else if (name.endsWith(".txt") || name.endsWith(".md") || name.endsWith(".java") || name.endsWith(".js") || name.endsWith(".xml")) {
            intent = new Intent(this, EditorActivity.class);
        } else {
            Toast.makeText(this, "No built-in viewer for this type.", Toast.LENGTH_SHORT).show();
            return;
        }
        intent.putExtra("filePath", file.getAbsolutePath());
        startActivity(intent);
    }

    @Override
    public void onBackPressed() {
        if (!currentDir.getAbsolutePath().equals(Environment.getExternalStorageDirectory().getAbsolutePath()) 
            && !currentDir.getAbsolutePath().equals("/")) {
            loadDirectory(currentDir.getParentFile());
        } else {
            super.onBackPressed();
        }
    }
}
