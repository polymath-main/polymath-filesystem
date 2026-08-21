package com.polymath.fs.viewers;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EditorActivity extends Activity {

    private EditText editor;
    private File currentFile;
    private boolean isModified = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        LinearLayout layout = new LinearLayout(this);
        layout.setBackgroundColor(0xFF0f172a);
        
        editor = new EditText(this);
        editor.setTextColor(0xFFf8fafc);
        editor.setBackgroundColor(0xFF0f172a);
        editor.setTypeface(Typeface.MONOSPACE);
        editor.setTextSize(14f);
        editor.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        
        layout.addView(editor, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 
                LinearLayout.LayoutParams.MATCH_PARENT));
        
        setContentView(layout);
        
        String path = getIntent().getStringExtra("filePath");
        if (path != null) {
            currentFile = new File(path);
            loadFile();
        }

        editor.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                isModified = true;
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void loadFile() {
        try {
            BufferedReader reader = new BufferedReader(new FileReader(currentFile));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            reader.close();
            
            // Simple Syntax Highlighting (Keywords)
            SpannableString spannable = new SpannableString(sb.toString());
            Pattern p = Pattern.compile("\\b(public|private|protected|class|void|import|package|int|float|boolean|return)\\b");
            Matcher m = p.matcher(spannable);
            while (m.find()) {
                spannable.setSpan(new ForegroundColorSpan(0xFF38bdf8), m.start(), m.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            
            editor.setText(spannable);
            isModified = false;
        } catch (Exception e) {
            Toast.makeText(this, "Failed to load file", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onBackPressed() {
        if (isModified && currentFile != null) {
            try {
                FileWriter writer = new FileWriter(currentFile);
                writer.write(editor.getText().toString());
                writer.close();
                Toast.makeText(this, "Saved: " + currentFile.getName(), Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "Failed to save", Toast.LENGTH_SHORT).show();
            }
        }
        super.onBackPressed();
    }
}
