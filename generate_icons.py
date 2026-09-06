import os

DRAWABLE_DIR = "/data/data/com.termux/files/home/Projects/polymath-filesystem/app/src/main/res/drawable"
os.makedirs(DRAWABLE_DIR, exist_ok=True)

types = ["code", "audio", "video", "archive", "apk", "pdf", "folder", "system", "hidden", "default"]
styles = {
    "fluent": {"fillColor": "#0078D4", "strokeWidth": "0"},
    "outline": {"fillColor": "#00000000", "strokeColor": "#333333", "strokeWidth": "2"},
    "solid": {"fillColor": "#4CAF50", "strokeWidth": "0"},
    "macos": {"fillColor": "#007AFF", "strokeWidth": "0", "gradient": True}
}

paths = {
    "code": "M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z",
    "audio": "M12 2v11.5A3.5 3.5 0 1 0 14 17V6h4V2z",
    "video": "M17 10.5V7a1 1 0 0 0-1-1H4a1 1 0 0 0-1 1v10a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-3.5l4 4v-11z",
    "archive": "M4 4h16v4H4zm2 4v12h12V8H6zm3 2h6v2H9v-2zm0 4h6v2H9v-2z",
    "apk": "M12 2L2 7l10 5 10-5zm0 20v-8L2 9v10l10 5zm2-8v8l10-5V9z",
    "pdf": "M20 2H8a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V4a2 2 0 0 0-2-2zM4 6H2v14a2 2 0 0 0 2 2h14v-2H4z",
    "folder": "M10 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z",
    "system": "M19.32 11.23a8.9 8.9 0 0 0 0-2.46l2.13-1.66-2-3.46-2.51 1a8.9 8.9 0 0 0-2.12-1.23L14.44 1h-4.88l-.38 2.42a8.9 8.9 0 0 0-2.12 1.23l-2.51-1-2 3.46 2.13 1.66a8.9 8.9 0 0 0 0 2.46l-2.13 1.66 2 3.46 2.51-1a8.9 8.9 0 0 0 2.12 1.23l.38 2.42h4.88l.38-2.42a8.9 8.9 0 0 0 2.12-1.23l2.51 1 2-3.46-2.13-1.66zM12 15.5A3.5 3.5 0 1 1 15.5 12 3.5 3.5 0 0 1 12 15.5z",
    "hidden": "M12 4.5C7 4.5 2.73 7.61 1 12c1.73 4.39 6 7.5 11 7.5s9.27-3.11 11-7.5c-1.73-4.39-6-7.5-11-7.5zM12 17c-2.76 0-5-2.24-5-5s2.24-5 5-5 5 2.24 5 5-2.24 5-5 5zm0-8c-1.66 0-3 1.34-3 3s1.34 3 3 3 3-1.34 3-3-1.34-3-3-3z",
    "default": "M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"
}

for style_name, style_props in styles.items():
    for t in types:
        filename = f"ic_{style_name}_{t}.xml"
        filepath = os.path.join(DRAWABLE_DIR, filename)
        
        fill_color = style_props.get("fillColor", "#000000")
        stroke_color = style_props.get("strokeColor", "#00000000")
        stroke_width = style_props.get("strokeWidth", "0")
        path_data = paths[t]
        
        content = f"""<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="{fill_color}"
        android:strokeColor="{stroke_color}"
        android:strokeWidth="{stroke_width}"
        android:pathData="{path_data}"/>
</vector>
"""
        with open(filepath, 'w') as f:
            f.write(content)
print("Icons generated successfully!")
