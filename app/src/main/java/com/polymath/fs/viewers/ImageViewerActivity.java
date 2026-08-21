package com.polymath.fs.viewers;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ImageViewerActivity extends Activity {

    private ImageView imageView;
    private Matrix matrix = new Matrix();
    private float scale = 1f;
    private ScaleGestureDetector scaleGestureDetector;
    private GestureDetector gestureDetector;
    
    private boolean isSlideshowRunning = false;
    private Handler slideshowHandler = new Handler(Looper.getMainLooper());
    private List<String> imageFiles = new ArrayList<>();
    private int currentIndex = 0;

    private Runnable slideshowRunnable = new Runnable() {
        @Override
        public void run() {
            if (imageFiles.isEmpty()) return;
            currentIndex = (currentIndex + 1) % imageFiles.size();
            loadImage(imageFiles.get(currentIndex));
            slideshowHandler.postDelayed(this, 3000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        imageView = new ImageView(this);
        imageView.setBackgroundColor(0xFF000000);
        imageView.setScaleType(ImageView.ScaleType.MATRIX);
        setContentView(imageView);

        String path = getIntent().getStringExtra("path");
        if (path == null) path = getIntent().getStringExtra("filePath");
        if (path != null) {
            File currentFile = new File(path);
            File parentDir = currentFile.getParentFile();
            if (parentDir != null && parentDir.isDirectory()) {
                File[] files = parentDir.listFiles(new FilenameFilter() {
                    @Override
                    public boolean accept(File dir, String name) {
                        String lower = name.toLowerCase();
                        return lower.endsWith(".png") || lower.endsWith(".jpg") || 
                               lower.endsWith(".jpeg") || lower.endsWith(".gif") || 
                               lower.endsWith(".webp");
                    }
                });
                if (files != null) {
                    Arrays.sort(files);
                    for (int i = 0; i < files.length; i++) {
                        imageFiles.add(files[i].getAbsolutePath());
                        if (files[i].getAbsolutePath().equals(path)) {
                            currentIndex = i;
                        }
                    }
                }
            }
            if (imageFiles.isEmpty()) {
                imageFiles.add(path);
            }
            loadImage(imageFiles.get(currentIndex));
        }

        scaleGestureDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                scale *= detector.getScaleFactor();
                scale = Math.max(0.1f, Math.min(scale, 5.0f));
                matrix.setScale(scale, scale, detector.getFocusX(), detector.getFocusY());
                imageView.setImageMatrix(matrix);
                return true;
            }
        });
        
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                toggleSlideshow();
                return true;
            }
        });
    }
    
    private void loadImage(String path) {
        Bitmap bitmap = BitmapFactory.decodeFile(path);
        imageView.setImageBitmap(bitmap);
        
        scale = 1f;
        matrix.reset();
        imageView.setImageMatrix(matrix);
    }
    
    private void toggleSlideshow() {
        if (isSlideshowRunning) {
            isSlideshowRunning = false;
            slideshowHandler.removeCallbacks(slideshowRunnable);
            Toast.makeText(this, "Slideshow paused", Toast.LENGTH_SHORT).show();
        } else {
            isSlideshowRunning = true;
            slideshowHandler.postDelayed(slideshowRunnable, 3000);
            Toast.makeText(this, "Slideshow started", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        gestureDetector.onTouchEvent(event);
        scaleGestureDetector.onTouchEvent(event);
        return true;
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        slideshowHandler.removeCallbacks(slideshowRunnable);
    }
}
