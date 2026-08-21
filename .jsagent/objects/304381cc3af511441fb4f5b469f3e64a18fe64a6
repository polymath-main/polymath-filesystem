package com.polymath.fs.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.polymath.fs.R;
import java.io.File;
import java.util.List;

public class ScriptAdapter extends RecyclerView.Adapter<ScriptAdapter.ScriptViewHolder> {

    private List<File> scripts;
    private OnScriptClickListener listener;

    public interface OnScriptClickListener { 
        void onRun(File script);
        void onEdit(File script);
        void onBookmark(File script);
    }

    public ScriptAdapter(List<File> scripts, OnScriptClickListener listener) {
        this.scripts = scripts;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ScriptViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ScriptViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_script, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ScriptViewHolder holder, int position) {
        holder.bind(scripts.get(position), listener);
    }

    @Override
    public int getItemCount() { return scripts.size(); }

    static class ScriptViewHolder extends RecyclerView.ViewHolder {
        TextView scriptName, scriptPath, btnRun, btnEdit, btnBookmark;

        ScriptViewHolder(View itemView) {
            super(itemView);
            scriptName = itemView.findViewById(R.id.scriptName);
            scriptPath = itemView.findViewById(R.id.scriptPath);
            btnRun = itemView.findViewById(R.id.btnRun);
            btnEdit = itemView.findViewById(R.id.btnEdit);
            btnBookmark = itemView.findViewById(R.id.btnBookmark);
        }
        
        void bind(File file, OnScriptClickListener listener) {
            scriptName.setText(file.getName());
            scriptPath.setText(file.getParent());

            btnRun.setOnClickListener(v -> listener.onRun(file));
            btnEdit.setOnClickListener(v -> listener.onEdit(file));
            btnBookmark.setOnClickListener(v -> listener.onBookmark(file));
        }
    }
}
