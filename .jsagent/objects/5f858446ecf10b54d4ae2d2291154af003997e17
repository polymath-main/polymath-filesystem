package com.polymath.fs.core;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONException;
import org.json.JSONObject;

public class ConfigManager {
    private static final String PREFS_NAME = "pfs_prefs";
    private static final String KEY_CONFIG = "config_json";
    
    private SharedPreferences prefs;
    private JSONObject currentConfig;
    private static ConfigManager instance;

    public static ConfigManager getInstance(Context context) {
        if (instance == null) instance = new ConfigManager(context.getApplicationContext());
        return instance;
    }

    private ConfigManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        loadConfig();
    }

    private void loadConfig() {
        String jsonStr = prefs.getString(KEY_CONFIG, getDefaultConfig().toString());
        try { currentConfig = new JSONObject(jsonStr); } catch (JSONException e) { currentConfig = getDefaultConfig(); }
    }

    public JSONObject getConfig() { return currentConfig; }

    private JSONObject getDefaultConfig() {
        try {
            JSONObject config = new JSONObject();
            JSONObject theme = new JSONObject();
            theme.put("primaryBg", "#0f172a");
            theme.put("secondaryBg", "#1e293b");
            theme.put("accentColor", "#38bdf8");
            theme.put("textColor", "#f8fafc");
            
            JSONObject ui = new JSONObject();
            ui.put("fontSize", 16);
            ui.put("iconSize", 24);
            
            config.put("theme", theme);
            config.put("ui", ui);
            return config;
        } catch (JSONException e) { return new JSONObject(); }
    }
}
