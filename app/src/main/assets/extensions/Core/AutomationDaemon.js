// Polymath File System - Core Modular Extension
// [AutomationDaemon.js]
// Example of native automated folder sorting via JS.

function startDaemon() {
    var downloadDir = "/sdcard/Download";
    
    // Attach the native FileObserver via PolymathOS bridge
    PolymathOS.listen("FILE_CREATED", downloadDir, "onNewFileDetected");
    PolymathOS.toast("Automation Daemon Online for: " + downloadDir);
}

// Global callback triggered natively by Java when a file is created
window.onNewFileDetected = function(filename) {
    var ext = filename.split('.').pop().toLowerCase();
    var path = "/sdcard/Download/" + filename;
    
    if (ext === 'apk') {
        PolymathOS.daemonCommand("execute_command", "mkdir -p /sdcard/Download/APKs && mv '" + path + "' '/sdcard/Download/APKs/'");
        PolymathOS.toast("Auto-Moved APK: " + filename);
    } else if (ext === 'jpg' || ext === 'png') {
        PolymathOS.daemonCommand("execute_command", "mkdir -p /sdcard/Download/Images && mv '" + path + "' '/sdcard/Download/Images/'");
        PolymathOS.toast("Auto-Moved Image: " + filename);
    }
};

// Boot the daemon
startDaemon();
