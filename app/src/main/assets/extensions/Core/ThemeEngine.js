// Polymath File System - Core Modular Extension
// [ThemeEngine.js]
// You can edit this file to completely transform the Android UI.

function applyNightMode() {
    var customTheme = {
        primaryBg: "#0f172a",    // Slate 900
        secondaryBg: "#1e293b",  // Slate 800
        textColor: "#f8fafc",    // Slate 50
        accentColor: "#3b82f6"   // Blue 500
    };
    
    // PolymathOS is our injected native bridge
    PolymathOS.setTheme(JSON.stringify(customTheme));
    PolymathOS.toast("Night Mode Modular Activated");
}

function applyDayMode() {
    var customTheme = {
        primaryBg: "#ffffff",
        secondaryBg: "#f1f5f9",
        textColor: "#0f172a",
        accentColor: "#2563eb"
    };
    PolymathOS.setTheme(JSON.stringify(customTheme));
}

// Automatically apply Night Mode on script execution
applyNightMode();
