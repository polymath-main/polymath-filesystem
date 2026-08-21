package com.polymath.fs.viewers;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.ImageView;

public class ImageViewerActivity extends Activity {

    private ImageView imageView;
    private Matrix matrix = new Matrix();
    private float scale = 1f;
    private ScaleGestureDetector scaleGestureDetector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        imageView = new ImageView(this);
        imageView.setBackgroundColor(0xFF000000);
        imageView.setScaleType(ImageView.ScaleType.MATRIX);
        setContentView(imageView);

        String path = getIntent().getStringExtra("filePath");
        if (path != null) {
            Bitmap bitmap = BitmapFactory.decodeFile(path);
            imageView.setImageBitmap(bitmap);
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
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleGestureDetector.onTouchEvent(event);
        return true;
    }
}
