package com.polymath.fs.models;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.webkit.MimeTypeMap;
import java.io.File;

public class SearchResultItem implements FileSystemItem {
    private final String name;
    private final String path;
    private final File file;

    public SearchResultItem(String name, String path) {
        this.name = name;
        this.path = path;
        this.file = new File(path);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getPath() {
        return path;
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
        if (!isDirectory()) return 0;
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
        return null; 
    }
}
