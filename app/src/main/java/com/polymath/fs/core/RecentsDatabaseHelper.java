package com.polymath.fs.core;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class RecentsDatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "recents.db";
    private static final int DATABASE_VERSION = 1;

    public static final String TABLE_ACCESS_LOGS = "access_logs";
    public static final String COLUMN_PATH = "path";
    public static final String COLUMN_TIMESTAMP = "timestamp";

    private static final String TABLE_CREATE =
            "CREATE TABLE " + TABLE_ACCESS_LOGS + " (" +
            COLUMN_PATH + " TEXT PRIMARY KEY, " +
            COLUMN_TIMESTAMP + " INTEGER" +
            ");";

    public RecentsDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(TABLE_CREATE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_ACCESS_LOGS);
        onCreate(db);
    }
}
