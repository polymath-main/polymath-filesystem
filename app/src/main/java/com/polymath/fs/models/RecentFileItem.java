package com.polymath.fs.models;

import android.content.Context;
import android.graphics.drawable.Drawable;

public class RecentFileItem implements FileSystemItem {

    private final LocalFileItem localFileItem;
    private final long accessTimestamp;

    public RecentFileItem(LocalFileItem localFileItem, long accessTimestamp) {
        this.localFileItem = localFileItem;
        this.accessTimestamp = accessTimestamp;
    }

    public LocalFileItem getLocalFileItem() {
        return localFileItem;
    }

    public long getAccessTimestamp() {
        return accessTimestamp;
    }

    @Override
    public String getName() {
        return localFileItem.getName();
    }

    @Override
    public String getPath() {
        return localFileItem.getPath();
    }

    @Override
    public long getSize() {
        return localFileItem.getSize();
    }

    @Override
    public long getLastModified() {
        return localFileItem.getLastModified();
    }

    @Override
    public boolean isDirectory() {
        return localFileItem.isDirectory();
    }

    @Override
    public String getMimeType() {
        return localFileItem.getMimeType();
    }

    @Override
    public Drawable getIcon(Context context) {
        return localFileItem.getIcon(context);
    }

    @Override
    public int getChildrenCount() {
        return localFileItem.getChildrenCount();
    }
}
