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
        TextView fileName, fileIcon;
        View container;
        FileViewHolder(View itemView) {
            super(itemView);
            fileName = itemView.findViewById(R.id.fileName);
            fileIcon = itemView.findViewById(R.id.fileIcon);
            container = itemView;
        }
        void bind(File file, OnItemClickListener listener, JSONObject config) {
            fileName.setText(file.getName());
            fileIcon.setText(file.isDirectory() ? "📁" : "📄");
            try {
                if (config != null) {
                    JSONObject theme = config.getJSONObject("theme");
                    fileName.setTextColor(Color.parseColor(theme.getString("textColor")));
                    fileName.setTextSize(config.getJSONObject("ui").getInt("fontSize"));
                    container.setBackgroundColor(Color.parseColor(theme.getString("primaryBg")));
                }
            } catch (Exception ignored) {}
            
            itemView.setOnClickListener(v -> listener.onItemClick(file));
            itemView.setOnLongClickListener(v -> {
                listener.onItemLongClick(file);
                return true;
            });
        }
    }
}
