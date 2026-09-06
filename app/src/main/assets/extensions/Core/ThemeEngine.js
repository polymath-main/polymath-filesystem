// Polymath File System - Core Modular Extension
// [ThemeEngine.js]
// Dynamic theme customization module.

function applyNightMode() {
    var customTheme = {
        name: "Night Slate",
        primaryBg: "#0f172a",    // Slate 900
        secondaryBg: "#1e293b",  // Slate 800
        textColor: "#f8fafc",    // Slate 50
        accentColor: "#3b82f6"   // Blue 500
    };
    PolymathOS.setTheme(JSON.stringify(customTheme));
    PolymathOS.toast("Theme Activated: Night Slate (Dark)");
}

function applyDayMode() {
    var customTheme = {
        name: "Light Minimal",
        primaryBg: "#ffffff",
        secondaryBg: "#f1f5f9",
        textColor: "#0f172a",
        accentColor: "#2563eb"
    };
    PolymathOS.setTheme(JSON.stringify(customTheme));
    PolymathOS.toast("Theme Activated: Light Minimal");
}

applyNightMode();
"ThemeEngine: Night Mode applied successfully";
