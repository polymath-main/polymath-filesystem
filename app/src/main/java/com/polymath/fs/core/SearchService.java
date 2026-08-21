package com.polymath.fs.core;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import java.io.File;

public class SearchService {
    
    public static void buildIndex(final Context context) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                SearchDatabaseHelper dbHelper = new SearchDatabaseHelper(context);
                SQLiteDatabase db = dbHelper.getWritableDatabase();
                
                // Clear old index
                db.execSQL("DELETE FROM " + SearchDatabaseHelper.TABLE_NAME);
                
                db.beginTransaction();
                SQLiteStatement stmt = null;
                try {
                    stmt = db.compileStatement("INSERT INTO " + SearchDatabaseHelper.TABLE_NAME + " (name, path) VALUES (?, ?)");
                    File root = new File("/sdcard");
                    scanDirectory(root, stmt);
                    db.setTransactionSuccessful();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    if (stmt != null) {
                        stmt.close();
                    }
                    db.endTransaction();
                    db.close();
                }
            }
        }).start();
    }
    
    private static void scanDirectory(File dir, SQLiteStatement stmt) {
        if (dir == null || !dir.exists() || !dir.canRead()) return;
        
        File[] files = dir.listFiles();
        if (files == null) return;
        
        for (File file : files) {
            stmt.bindString(1, file.getName());
            stmt.bindString(2, file.getAbsolutePath());
            stmt.executeInsert();
            stmt.clearBindings();
            
            if (file.isDirectory()) {
                scanDirectory(file, stmt);
            }
        }
    }
}
