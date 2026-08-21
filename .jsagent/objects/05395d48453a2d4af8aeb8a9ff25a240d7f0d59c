package com.polymath.fs.viewers;

import android.app.Activity;
import android.os.Bundle;
import android.widget.FrameLayout;
import android.widget.MediaController;
import android.widget.VideoView;
import android.view.Gravity;

public class MediaPlayerActivity extends Activity {

    private VideoView videoView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        FrameLayout layout = new FrameLayout(this);
        layout.setBackgroundColor(0xFF000000);
        
        videoView = new VideoView(this);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, 
                FrameLayout.LayoutParams.MATCH_PARENT);
        params.gravity = Gravity.CENTER;
        layout.addView(videoView, params);
        
        setContentView(layout);
        
        String filePath = getIntent().getStringExtra("filePath");
        
        MediaController mediaController = new MediaController(this);
        mediaController.setAnchorView(videoView);
        videoView.setMediaController(mediaController);
        
        if (filePath != null) {
            videoView.setVideoPath(filePath);
            videoView.start();
        }
    }
}
