package com.polymath.fs.core;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class SearchDatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "search_index.db";
    private static final int DATABASE_VERSION = 1;
    public static final String TABLE_NAME = "files";

    public SearchDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE VIRTUAL TABLE " + TABLE_NAME + " USING fts4(name, path)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
        onCreate(db);
    }
}
