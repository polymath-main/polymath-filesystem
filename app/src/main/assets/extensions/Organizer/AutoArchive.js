// Polymath File System - Organizer Module
// [AutoArchive.js]
// Automatic background archiving of log files, temporary crash dumps, and old documents.

function onNewDownload(event) {
    if (event && event.file && (event.file.endsWith(".log") || event.file.endsWith(".tmp"))) {
        PolymathOS.daemonCommand("archive", event.file);
        PolymathOS.toast("Auto-Archived: " + event.file);
    }
}

PolymathOS.listen("FILE_CREATED", "/storage/emulated/0/Download", "onNewDownload");

// Scan and archive any existing log or tmp files in Download
var res = PolymathOS.daemonCommand("execute_command", "cd /storage/emulated/0/Download && zip -m -r old_logs.zip *.log *.tmp 2>/dev/null || true");
PolymathOS.toast("AutoArchive: Listener active on Download folder");
"AutoArchive Active: Monitoring and auto-compressing logs";
