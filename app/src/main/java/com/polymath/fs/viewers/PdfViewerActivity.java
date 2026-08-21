package com.polymath.fs.viewers;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.File;

public class PdfViewerActivity extends Activity {
    private ImageView imageView;
    private PdfRenderer pdfRenderer;
    private PdfRenderer.Page currentPage;
    private int pageIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(0xFFFFFFFF);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        
        Button prev = new Button(this);
        prev.setText("Prev");
        Button next = new Button(this);
        next.setText("Next");

        controls.addView(prev);
        controls.addView(next);
        
        imageView = new ImageView(this);
        imageView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
                
        layout.addView(controls);
        layout.addView(imageView);
        
        setContentView(layout);

        String path = getIntent().getStringExtra("path");
        if (path == null) path = getIntent().getStringExtra("filePath");
        try {
            File file = new File(path);
            ParcelFileDescriptor pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
            pdfRenderer = new PdfRenderer(pfd);
            showPage(pageIndex);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error loading PDF", Toast.LENGTH_SHORT).show();
        }
        
        prev.setOnClickListener(v -> {
            if (pageIndex > 0) {
                pageIndex--;
                showPage(pageIndex);
            }
        });
        
        next.setOnClickListener(v -> {
            if (pdfRenderer != null && pageIndex < pdfRenderer.getPageCount() - 1) {
                pageIndex++;
                showPage(pageIndex);
            }
        });
    }

    private void showPage(int index) {
        if (pdfRenderer == null || pdfRenderer.getPageCount() <= index) return;
        if (currentPage != null) currentPage.close();
        currentPage = pdfRenderer.openPage(index);
        Bitmap bitmap = Bitmap.createBitmap(currentPage.getWidth() * 2, currentPage.getHeight() * 2, Bitmap.Config.ARGB_8888);
        currentPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
        imageView.setImageBitmap(bitmap);
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (currentPage != null) currentPage.close();
        if (pdfRenderer != null) pdfRenderer.close();
    }
}
