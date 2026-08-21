package com.polymath.fs.ui;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.polymath.fs.R;
import org.json.JSONObject;
import java.io.File;
import java.util.List;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class FileAdapter extends RecyclerView.Adapter<FileAdapter.FileViewHolder> {

    private List<File> files;
    private OnItemClickListener listener;
    private JSONObject config;

    public interface OnItemClickListener { 
        void onItemClick(File file); 
        void onItemLongClick(File file);
    }

    public FileAdapter(List<File> files, OnItemClickListener listener) {
        this.files = files;
        this.listener = listener;
    }
    
    public void setConfig(JSONObject config) { this.config = config; }

    @NonNull
    @Override
    public FileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new FileViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_file, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull FileViewHolder holder, int position) {
        holder.bind(files.get(position), listener, config);
    }

    @Override
    public int getItemCount() { return files.size(); }

    static class FileViewHolder extends RecyclerView.ViewHolder {
        TextView fileName, fileIcon, fileDetails, btnMenu;

        FileViewHolder(View itemView) {
            super(itemView);
            fileName = itemView.findViewById(R.id.fileName);
            fileIcon = itemView.findViewById(R.id.fileIcon);
            fileDetails = itemView.findViewById(R.id.fileDetails);
            btnMenu = itemView.findViewById(R.id.btnMenu);
        }
        
        void bind(File file, OnItemClickListener listener, JSONObject config) {
            fileName.setText(file.getName());

            // Apply JS Dynamic Theme if available
            if (config != null) {
                try {
                    JSONObject theme = config.optJSONObject("theme");
                    if (theme != null) {
                        if (itemView instanceof androidx.cardview.widget.CardView) {
                            ((androidx.cardview.widget.CardView) itemView).setCardBackgroundColor(Color.parseColor(theme.optString("secondaryBg", "#1e293b")));
                        }
                        fileName.setTextColor(Color.parseColor(theme.optString("textColor", "#f8fafc")));
                    }
                } catch (Exception ignored) {}
            }
            
            // Format Subtitle
            String details = "";
            if (file.isDirectory()) {
                String[] list = file.list();
                int count = (list != null) ? list.length : 0;
                details = count + " items";
                fileIcon.setText("📁");
            } else {
                long length = file.length();
                details = formatSize(length);
                fileIcon.setText(getEmojiForFile(file.getName()));
            }
            
            // Add Date
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
            details += " • " + sdf.format(new Date(file.lastModified()));
            fileDetails.setText(details);

            // Bind Clicks
            itemView.setOnClickListener(v -> listener.onItemClick(file));
            itemView.setOnLongClickListener(v -> {
                listener.onItemLongClick(file);
                return true;
            });
            btnMenu.setOnClickListener(v -> listener.onItemLongClick(file));
        }

        private String formatSize(long size) {
            if (size <= 0) return "0 B";
            final String[] units = new String[] { "B", "KB", "MB", "GB", "TB" };
            int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
            return new java.text.DecimalFormat("#,##0.#").format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
        }

        private String getEmojiForFile(String name) {
            name = name.toLowerCase();
            if (name.endsWith(".jpg") || name.endsWith(".png") || name.endsWith(".gif")) return "🖼️";
            if (name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".avi")) return "🎬";
            if (name.endsWith(".mp3") || name.endsWith(".wav")) return "🎵";
            if (name.endsWith(".zip") || name.endsWith(".tar") || name.endsWith(".gz")) return "📦";
            if (name.endsWith(".pdf")) return "📕";
            if (name.endsWith(".js") || name.endsWith(".java") || name.endsWith(".xml") || name.endsWith(".py")) return "💻";
            return "📄";
        }
    }
}
