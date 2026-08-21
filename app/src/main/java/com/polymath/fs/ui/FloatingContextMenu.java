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
        background.setCornerRadius(dpToPx(16)); // Updated to 16dp
        
        setBackground(background);
        setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));
        setElevation(dpToPx(8));
        
        setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int maxWidth = dpToPx(280);
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        
        if (widthMode == MeasureSpec.UNSPECIFIED || widthSize > maxWidth) {
            widthMeasureSpec = MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.AT_MOST);
        }
        
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    public void setOnActionClickListener(OnActionClickListener listener) {
        this.listener = listener;
    }

    public void setActions(String headerTitle, List<String> actions) {
        removeAllViews();
        
        if (headerTitle != null && !headerTitle.isEmpty()) {
            TextView headerView = new TextView(getContext());
            headerView.setText(headerTitle);
            headerView.setTextColor(Color.parseColor("#94a3b8"));
            headerView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            headerView.setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(8));
            headerView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            addView(headerView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            
            View divider = new View(getContext());
            divider.setBackgroundColor(Color.parseColor("#334155"));
            addView(divider, new LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(1)));
        }
        
        if (actions == null || actions.isEmpty()) {
            return;
        }

        boolean prevWasNative = false;
        for (int i = 0; i < actions.size(); i++) {
            String action = actions.get(i);
            boolean isJs = action.startsWith("⚡ ");
            
            if (i > 0) {
                if (isJs && prevWasNative) {
                    // Section divider
                    View sectionDivider = new View(getContext());
                    sectionDivider.setBackgroundColor(Color.parseColor("#0f172a"));
                    addView(sectionDivider, new LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(4)));
                } else {
                    // Item divider
                    View divider = new View(getContext());
                    divider.setBackgroundColor(Color.parseColor("#334155"));
                    addView(divider, new LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(1)));
                }
            }
            prevWasNative = !isJs;

            TextView actionView = new TextView(getContext());
            actionView.setText(action);
            actionView.setTextColor(Color.WHITE);
            actionView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            actionView.setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12));
            actionView.setGravity(Gravity.CENTER_VERTICAL);
            actionView.setMinHeight(dpToPx(52));
            
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

    public void setActions(List<String> actions) {
        setActions(null, actions);
    }

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                getResources().getDisplayMetrics()
        );
    }
}
