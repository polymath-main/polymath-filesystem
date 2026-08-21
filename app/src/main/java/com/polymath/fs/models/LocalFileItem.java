package com.polymath.fs.models;

import android.content.Context;
import android.graphics.drawable.Drawable;
import java.io.File;
import android.webkit.MimeTypeMap;

public class LocalFileItem implements FileSystemItem {
    private final File file;

    public LocalFileItem(File file) {
        this.file = file;
    }

    public File getFile() {
        return file;
    }

    @Override
    public String getName() {
        return file.getName();
    }

    @Override
    public String getPath() {
        return file.getAbsolutePath();
    }

    @Override
    public long getSize() {
        return file.length();
    }

    @Override
    public long getLastModified() {
        return file.lastModified();
    }

    @Override
    public boolean isDirectory() {
        return file.isDirectory();
    }

    @Override
    public int getChildrenCount() {
        if (!file.isDirectory()) return 0;
        String[] list = file.list();
        return list != null ? list.length : 0;
    }

    @Override
    public String getMimeType() {
        if (isDirectory()) return "*/*";
        String extension = MimeTypeMap.getFileExtensionFromUrl(file.getAbsolutePath());
        if (extension != null) {
            String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase());
            if (mime != null) return mime;
        }
        return "*/*";
    }

    @Override
    public Drawable getIcon(Context context) {
        // Fallback for native icons if needed, but we typically use emojis in the UI
        return null; 
    }
}
