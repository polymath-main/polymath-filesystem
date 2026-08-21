package com.polymath.fs.core;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

public class RecentsManager {

    public static void logAccess(Context context, String path) {
        RecentsDatabaseHelper dbHelper = new RecentsDatabaseHelper(context);
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(RecentsDatabaseHelper.COLUMN_PATH, path);
        values.put(RecentsDatabaseHelper.COLUMN_TIMESTAMP, System.currentTimeMillis());

        db.replace(RecentsDatabaseHelper.TABLE_ACCESS_LOGS, null, values);
        db.close();
    }

    public static List<String> getRecentPaths(Context context, int limit) {
        RecentsDatabaseHelper dbHelper = new RecentsDatabaseHelper(context);
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        
        List<String> paths = new ArrayList<>();
        
        String query = "SELECT " + RecentsDatabaseHelper.COLUMN_PATH + " FROM " + 
                       RecentsDatabaseHelper.TABLE_ACCESS_LOGS + 
                       " ORDER BY " + RecentsDatabaseHelper.COLUMN_TIMESTAMP + " DESC " +
                       " LIMIT " + limit;
                       
        Cursor cursor = db.rawQuery(query, null);
        
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                do {
                    int pathIndex = cursor.getColumnIndex(RecentsDatabaseHelper.COLUMN_PATH);
                    if (pathIndex != -1) {
                        paths.add(cursor.getString(pathIndex));
                    }
                } while (cursor.moveToNext());
            }
            cursor.close();
        }
        
        db.close();
        return paths;
    }
}
