import sys

file_path = "/data/data/com.termux/files/home/Projects/polymath-filesystem/app/src/main/java/com/polymath/fs/ui/JsDashboardActivity.java"

with open(file_path, "r") as f:
    content = f.read()

load_extensions_orig = """    private void loadExtensions() {
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
    }"""

load_extensions_new = """    private void loadExtensions() {
        new Thread(() -> {
            List<ExtensionInfo> loadedList = new ArrayList<>();
            String[] bundled = {"AutoOrganizer", "Core", "GhostVault", "Network", "Organizer", "Security", "SystemAnalytics", "Themes", "Utils"};
            for (String ext : bundled) {
                ExtensionInfo info = new ExtensionInfo();
                info.name = ext;
                info.description = "Bundled System Module";
                info.icon = "";
                info.configurable = false;
                info.isAsset = true;
                info.dirPath = "extensions/" + ext;
                
                try {
                    java.io.InputStream is = getAssets().open(info.dirPath + "/manifest.json");
                    int size = is.available();
                    byte[] buffer = new byte[size];
                    is.read(buffer);
                    is.close();
                    JSONObject json = new JSONObject(new String(buffer, "UTF-8"));
                    info.name = json.optString("name", info.name);
                    info.description = json.optString("description", info.description);
                    info.icon = json.optString("icon", "");
                    info.configurable = json.optBoolean("configurable", false);
                } catch (Exception e) {}
                loadedList.add(info);
            }

            File extDir = new File("/sdcard/PolymathExtensions/");
            if (extDir.exists() && extDir.isDirectory()) {
                File[] subdirs = extDir.listFiles(File::isDirectory);
                if (subdirs != null) {
                    for (File dir : subdirs) {
                        File manifestFile = new File(dir, "manifest.json");
                        if (manifestFile.exists()) {
                            try {
                                StringBuilder jsonStr = new StringBuilder();
                                BufferedReader reader = new BufferedReader(new FileReader(manifestFile));
                                String line;
                                while ((line = reader.readLine()) != null) jsonStr.append(line);
                                reader.close();

                                JSONObject json = new JSONObject(jsonStr.toString());
                                ExtensionInfo info = new ExtensionInfo();
                                info.name = json.optString("name", "Unknown Extension");
                                info.description = json.optString("description", "No description available.");
                                info.icon = json.optString("icon", "");
                                info.configurable = json.optBoolean("configurable", false);
                                info.dirPath = dir.getAbsolutePath();
                                info.isAsset = false;
                                loadedList.add(info);
                            } catch (Exception e) {}
                        }
                    }
                }
            }
            
            runOnUiThread(() -> {
                extensionList.clear();
                extensionList.addAll(loadedList);
                adapter.notifyDataSetChanged();
                
                TextView tvEmpty = findViewById(R.id.tv_empty);
                if (loadedList.isEmpty()) {
                    tvEmpty.setVisibility(View.VISIBLE);
                    recyclerView.setVisibility(View.GONE);
                } else {
                    tvEmpty.setVisibility(View.GONE);
                    recyclerView.setVisibility(View.VISIBLE);
                }
            });
        }).start();
    }"""

ext_info_orig = """    private static class ExtensionInfo {
        String name;
        String description;
        String icon;
        boolean configurable;
        String dirPath;
    }"""

ext_info_new = """    private static class ExtensionInfo {
        String name;
        String description;
        String icon;
        boolean configurable;
        String dirPath;
        boolean isAsset;
    }"""

bind_orig = """        @Override
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
        }"""

bind_new = """        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ExtensionInfo info = items.get(position);
            holder.tvName.setText(info.name);
            holder.tvDescription.setText(info.description);
            holder.ivConfigurable.setVisibility(info.configurable ? View.VISIBLE : View.GONE);
            
            if (!info.icon.isEmpty()) {
                if (info.isAsset) {
                    try {
                        java.io.InputStream is = holder.itemView.getContext().getAssets().open(info.dirPath + "/" + info.icon);
                        Bitmap bmp = BitmapFactory.decodeStream(is);
                        if (bmp != null) holder.ivIcon.setImageBitmap(bmp);
                        is.close();
                    } catch (Exception e) {}
                } else {
                    File iconFile = new File(info.dirPath, info.icon);
                    if (iconFile.exists()) {
                        Bitmap bmp = BitmapFactory.decodeFile(iconFile.getAbsolutePath());
                        if (bmp != null) holder.ivIcon.setImageBitmap(bmp);
                    }
                }
            } else {
                holder.ivIcon.setImageResource(android.R.drawable.ic_menu_help);
            }

            holder.btnRun.setOnClickListener(v -> {
                android.content.Context ctx = holder.itemView.getContext();
                if (info.isAsset) {
                    try {
                        String[] files = ctx.getAssets().list(info.dirPath);
                        if (files != null) {
                            for (String file : files) {
                                if (file.endsWith(".js")) {
                                    java.io.InputStream is = ctx.getAssets().open(info.dirPath + "/" + file);
                                    File outFile = new File(ctx.getCacheDir(), file);
                                    java.io.FileOutputStream fos = new java.io.FileOutputStream(outFile);
                                    byte[] buffer = new byte[1024];
                                    int length;
                                    while ((length = is.read(buffer)) > 0) fos.write(buffer, 0, length);
                                    fos.close();
                                    is.close();
                                    com.polymath.fs.core.JsRuntimeManager.executeScript(ctx, outFile);
                                }
                            }
                        }
                    } catch (Exception e) {}
                } else if (info.dirPath != null) {
                    File dir = new File(info.dirPath);
                    File[] jsFiles = dir.listFiles((d, name) -> name.endsWith(".js"));
                    if (jsFiles != null) {
                        for (File js : jsFiles) com.polymath.fs.core.JsRuntimeManager.executeScript(ctx, js);
                    }
                }
                Toast.makeText(ctx, "Running: " + info.name, Toast.LENGTH_SHORT).show();
            });
        }"""

view_holder_orig = """        class ViewHolder extends RecyclerView.ViewHolder {
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
        }"""

view_holder_new = """        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvName;
            TextView tvDescription;
            ImageView ivIcon;
            ImageView ivConfigurable;
            android.widget.Button btnRun;

            ViewHolder(View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tv_name);
                tvDescription = itemView.findViewById(R.id.tv_description);
                ivIcon = itemView.findViewById(R.id.iv_icon);
                ivConfigurable = itemView.findViewById(R.id.iv_configurable);
                btnRun = itemView.findViewById(R.id.btn_run);
            }
        }"""

content = content.replace(load_extensions_orig, load_extensions_new)
content = content.replace(ext_info_orig, ext_info_new)
content = content.replace(bind_orig, bind_new)
content = content.replace(view_holder_orig, view_holder_new)

with open(file_path, "w") as f:
    f.write(content)

print("Done")
