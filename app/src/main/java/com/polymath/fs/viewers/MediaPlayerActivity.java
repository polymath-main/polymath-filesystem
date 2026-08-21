package com.polymath.fs.viewers;

import android.app.Activity;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.FrameLayout;

public class MediaPlayerActivity extends Activity implements SurfaceHolder.Callback {

    private MediaPlayer mediaPlayer;
    private SurfaceView surfaceView;
    private String filePath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        FrameLayout layout = new FrameLayout(this);
        layout.setBackgroundColor(0xFF000000);
        
        surfaceView = new SurfaceView(this);
        layout.addView(surfaceView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, 
                FrameLayout.LayoutParams.MATCH_PARENT));
        
        setContentView(layout);
        
        filePath = getIntent().getStringExtra("filePath");
        surfaceView.getHolder().addCallback(this);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        try {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDisplay(holder);
            mediaPlayer.setDataSource(filePath);
            mediaPlayer.prepare();
            mediaPlayer.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }
}
