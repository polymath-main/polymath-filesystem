package com.polymath.fs.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.polymath.fs.R;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

public class JsDashboardActivity extends AppCompatActivity {
    
    private static final int PERMISSION_REQUEST_CODE = 1001;
    private RecyclerView recyclerView;
    private ExtensionAdapter adapter;
    private List<ExtensionInfo> extensionList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_js_dashboard);

        recyclerView = findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        adapter = new ExtensionAdapter(extensionList);
        recyclerView.setAdapter(adapter);

        if (checkPermission()) {
            loadExtensions();
        } else {
            requestPermission();
        }
    }

    private boolean checkPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermission() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, PERMISSION_REQUEST_CODE);
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadExtensions();
            } else {
                Toast.makeText(this, "Storage permission required to scan extensions", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void loadExtensions() {
        new Thread(() -> {
            File extDir = new File("/sdcard/PolymathExtensions/");
            if (!extDir.exists() || !extDir.isDirectory()) {
                Log.e("JsDashboard", "Extensions directory not found");
                return;
            }

            File[] subdirs = extDir.listFiles(File::isDirectory);
            if (subdirs == null) return;

            List<ExtensionInfo> loadedList = new ArrayList<>();

            for (File dir : subdirs) {
                File manifestFile = new File(dir, "manifest.json");
                if (manifestFile.exists()) {
                    try {
                        StringBuilder jsonStr = new StringBuilder();
                        BufferedReader reader = new BufferedReader(new FileReader(manifestFile));
                        String line;
                        while ((line = reader.readLine()) != null) {
                            jsonStr.append(line);
                        }
                        reader.close();

                        JSONObject json = new JSONObject(jsonStr.toString());
                        ExtensionInfo info = new ExtensionInfo();
                        info.name = json.optString("name", "Unknown Extension");
                        info.description = json.optString("description", "No description available.");
                        info.icon = json.optString("icon", "");
                        info.configurable = json.optBoolean("configurable", false);
                        info.dirPath = dir.getAbsolutePath();
                        
                        loadedList.add(info);
                    } catch (Exception e) {
                        Log.e("JsDashboard", "Error parsing manifest in " + dir.getName(), e);
                    }
                }
            }
            
            runOnUiThread(() -> {
                extensionList.clear();
                extensionList.addAll(loadedList);
                adapter.notifyDataSetChanged();
            });
        }).start();
    }

    private static class ExtensionInfo {
        String name;
        String description;
        String icon;
        boolean configurable;
        String dirPath;
    }

    private class ExtensionAdapter extends RecyclerView.Adapter<ExtensionAdapter.ViewHolder> {

        private List<ExtensionInfo> items;

        ExtensionAdapter(List<ExtensionInfo> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_extension, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ExtensionInfo info = items.get(position);
            holder.tvName.setText(info.name);
            holder.tvDescription.setText(info.description);
            holder.ivConfigurable.setVisibility(info.configurable ? View.VISIBLE : View.GONE);
            
            if (!info.icon.isEmpty()) {
                File iconFile = new File(info.dirPath, info.icon);
                if (iconFile.exists()) {
                    Bitmap bmp = BitmapFactory.decodeFile(iconFile.getAbsolutePath());
                    if (bmp != null) {
                        holder.ivIcon.setImageBitmap(bmp);
                    }
                }
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvName;
            TextView tvDescription;
            ImageView ivIcon;
            ImageView ivConfigurable;

            ViewHolder(View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tv_name);
                tvDescription = itemView.findViewById(R.id.tv_description);
                ivIcon = itemView.findViewById(R.id.iv_icon);
                ivConfigurable = itemView.findViewById(R.id.iv_configurable);
            }
        }
    }
}
