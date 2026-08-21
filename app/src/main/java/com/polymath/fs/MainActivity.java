package com.polymath.fs;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.net.Uri;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.widget.FrameLayout;
import android.widget.Toast;
import android.view.LayoutInflater;
import android.view.View;
import android.graphics.Color;
import android.widget.TextView;
import android.view.Gravity;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.polymath.fs.core.ConfigManager;
import com.polymath.fs.core.JsRuntimeManager;
import com.polymath.fs.models.FileSystemItem;
import com.polymath.fs.ui.FileAdapter;
import com.polymath.fs.viewmodels.FileSystemViewModel;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileOutputStream;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    // Canvas Containers
    private FrameLayout headerContainer;
    private FrameLayout contentContainer;
    private FrameLayout overlayContainer;

    // State & Bridge
    private FileSystemViewModel viewModel;
    private FileAdapter adapter;
    private ConfigManager configManager;
    private final File extensionsDir = new File(Environment.getExternalStorageDirectory(), "PolymathExtensions");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        configManager = ConfigManager.getInstance(this);
        viewModel = new FileSystemViewModel(); // In a pure ViewModel setup, we'd use ViewModelProvider

        headerContainer = findViewById(R.id.headerContainer);
        contentContainer = findViewById(R.id.contentContainer);
        overlayContainer = findViewById(R.id.overlayContainer);

        checkAndRequestPermissions();
    }

    private void checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.addCategory("android.intent.category.DEFAULT");
                    intent.setData(Uri.parse(String.format("package:%s", getApplicationContext().getPackageName())));
                    startActivityForResult(intent, 2296);
                } catch (Exception e) {
                    Intent intent = new Intent();
                    intent.setAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    startActivityForResult(intent, 2296);
                }
            } else {
                initializeApp();
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, 100);
                } else {
                    initializeApp();
                }
            } else {
                initializeApp();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == 100 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            initializeApp();
        } else {
            Toast.makeText(this, "Permission Denied. Features limited.", Toast.LENGTH_LONG).show();
            initializeApp();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 2296) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    initializeApp();
                } else {
                    Toast.makeText(this, "All Files Access Denied. Fallback to Root.", Toast.LENGTH_LONG).show();
                    initializeApp();
                }
            }
        }
    }

    private void initializeApp() {
        if (!extensionsDir.exists()) {
            extensionsDir.mkdirs();
            extractBuiltInExtensions();
        }

        // Setup Content RecyclerView
        RecyclerView fileRecyclerView = findViewById(R.id.fileRecyclerView);
        fileRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Observe ViewModel
        viewModel.getFileList().observe(this, files -> {
            if (adapter == null) {
                adapter = new FileAdapter(files, new FileAdapter.OnItemClickListener() {
                    @Override
                    public void onItemClick(FileSystemItem file) {
                        if (file.isDirectory()) {
                            viewModel.navigateTo(file.getPath());
                        } else {
                            // TODO: Add Viewer routing in Phase 2
                            Toast.makeText(MainActivity.this, "File: " + file.getName(), Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onItemLongClick(FileSystemItem file) {
                        // TODO: Context Menu in Phase 2
                        Toast.makeText(MainActivity.this, "Context: " + file.getName(), Toast.LENGTH_SHORT).show();
                    }
                });
                fileRecyclerView.setAdapter(adapter);
            } else {
                // Update files in a real app this would use DiffUtil
                adapter = new FileAdapter(files, new FileAdapter.OnItemClickListener() {
                    @Override
                    public void onItemClick(FileSystemItem file) {
                        if (file.isDirectory()) {
                            viewModel.navigateTo(file.getPath());
                        }
                    }
                    @Override
                    public void onItemLongClick(FileSystemItem file) {}
                });
                fileRecyclerView.setAdapter(adapter);
            }
            adapter.setConfig(configManager.getConfig());
        });

        viewModel.getCurrentPath().observe(this, path -> {
            renderHeader(path);
        });

        // Initial Load
        viewModel.navigateTo(Environment.getExternalStorageDirectory().getAbsolutePath());

        // Boot Extensions
        bootCoreModules();
    }
    
    public void applyTheme() {
        try {
            org.json.JSONObject theme = configManager.getConfig().getJSONObject("theme");
            findViewById(android.R.id.content).setBackgroundColor(Color.parseColor(theme.getString("primaryBg")));
            headerContainer.setBackgroundColor(Color.parseColor(theme.getString("secondaryBg")));
            
            if (adapter != null) {
                adapter.setConfig(configManager.getConfig());
                adapter.notifyDataSetChanged();
            }
        } catch (Exception ignored) {}
    }

    private void renderHeader(String path) {
        headerContainer.removeAllViews();
        TextView pathView = new TextView(this);
        pathView.setText(path);
        pathView.setTextColor(Color.WHITE);
        pathView.setPadding(32, 32, 32, 32);
        pathView.setTextSize(16);
        headerContainer.addView(pathView);
    }

    private void bootCoreModules() {
        File coreDir = new File(extensionsDir, "Core");
        if (coreDir.exists()) {
            File[] scripts = coreDir.listFiles();
            if (scripts != null) {
                for (File script : scripts) {
                    if (script.getName().endsWith(".js")) {
                        JsRuntimeManager.executeScript(this, script);
                    }
                }
            }
        }
    }

    private void extractBuiltInExtensions() {
        try {
            String[] categories = getAssets().list("extensions");
            if (categories == null) return;
            for (String category : categories) {
                File catDir = new File(extensionsDir, category);
                catDir.mkdirs();
                String[] files = getAssets().list("extensions/" + category);
                if (files != null) {
                    for (String file : files) {
                        InputStream is = getAssets().open("extensions/" + category + "/" + file);
                        File outFile = new File(catDir, file);
                        OutputStream os = new FileOutputStream(outFile);
                        byte[] buffer = new byte[1024];
                        int read;
                        while ((read = is.read(buffer)) != -1) {
                            os.write(buffer, 0, read);
                        }
                        is.close();
                        os.flush();
                        os.close();
                    }
                }
            }
            Toast.makeText(this, "Extensions extracted!", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    // Fallback for ComponentActivity/LifecycleOwner if using raw Activity. 
    // In a real app we'd extend AppCompatActivity to use .observe() directly.
    // For now, let's implement a simple Observer pattern in ViewModel if LiveData isn't fully available on pure Activity.
}
