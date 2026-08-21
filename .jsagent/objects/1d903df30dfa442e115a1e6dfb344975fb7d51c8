package com.polymath.fs.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.List;

public class FloatingContextMenu extends LinearLayout {

    public interface OnActionClickListener {
        void onActionClick(String actionName);
    }

    private OnActionClickListener listener;

    public FloatingContextMenu(Context context) {
        super(context);
        init();
    }

    public FloatingContextMenu(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public FloatingContextMenu(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setOrientation(VERTICAL);
        
        // Premium Material 3 styling
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.parseColor("#1e293b"));
        background.setCornerRadius(dpToPx(12));
        
        setBackground(background);
        setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));
        setElevation(dpToPx(8));
        
        setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
    }

    public void setOnActionClickListener(OnActionClickListener listener) {
        this.listener = listener;
    }

    public void setActions(List<String> actions) {
        removeAllViews();
        if (actions == null || actions.isEmpty()) {
            return;
        }

        for (String action : actions) {
            TextView actionView = new TextView(getContext());
            actionView.setText(action);
            actionView.setTextColor(Color.WHITE);
            actionView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            actionView.setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12));
            actionView.setGravity(Gravity.CENTER_VERTICAL);
            
            // Ripple effect for clickable item
            TypedValue outValue = new TypedValue();
            getContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
            actionView.setBackgroundResource(outValue.resourceId);

            actionView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onActionClick(action);
                }
            });

            LayoutParams params = new LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT
            );
            addView(actionView, params);
        }
    }

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                getResources().getDisplayMetrics()
        );
    }
}
