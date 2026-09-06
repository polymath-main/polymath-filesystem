// Polymath File System - Core Modular Extension
// [AutomationDaemon.js]
// Real-time file monitoring daemon for automated background categorization.

function startDaemon() {
    var downloadDir = "/storage/emulated/0/Download";
    
    // Attach listener via PolymathOS bridge
    PolymathOS.listen("FILE_CREATED", downloadDir, "onNewFileDetected");
    PolymathOS.toast("Automation Daemon Active: Monitoring " + downloadDir);
}

// Global callback triggered when a new file is detected
window.onNewFileDetected = function(filename) {
    if (!filename) return;
    var ext = filename.split('.').pop().toLowerCase();
    var path = "/storage/emulated/0/Download/" + filename;
    
    if (ext === 'apk') {
        PolymathOS.daemonCommand("execute_command", "mkdir -p /storage/emulated/0/Download/APKs && mv '" + path + "' '/storage/emulated/0/Download/APKs/'");
        PolymathOS.toast("Auto-Moved APK: " + filename);
    } else if (ext === 'jpg' || ext === 'png' || ext === 'jpeg') {
        PolymathOS.daemonCommand("execute_command", "mkdir -p /storage/emulated/0/Download/Images && mv '" + path + "' '/storage/emulated/0/Download/Images/'");
        PolymathOS.toast("Auto-Moved Image: " + filename);
    } else if (ext === 'pdf' || ext === 'doc' || ext === 'txt') {
        PolymathOS.daemonCommand("execute_command", "mkdir -p /storage/emulated/0/Download/Documents && mv '" + path + "' '/storage/emulated/0/Download/Documents/'");
        PolymathOS.toast("Auto-Moved Document: " + filename);
    }
};

startDaemon();
"Automation Daemon Online: Monitoring Downloads";
