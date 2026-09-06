// Polymath File System - Organizer Module
// [CacheCleaner.js]
// Sweeps app caches, residual thumbnail folders, and temporary download residues.

(function() {
    PolymathOS.toast("CacheCleaner: Purging temporary cache buffers...");
    var cmd = 'rm -rf /storage/emulated/0/Android/data/*/cache/* 2>/dev/null; ' +
              'rm -rf /storage/emulated/0/DCIM/.thumbnails/* 2>/dev/null; ' +
              'rm -rf /storage/emulated/0/Pictures/.thumbnails/* 2>/dev/null; ' +
              'echo "Cache cleaning finished"';
    var res = PolymathOS.daemonCommand("execute_command", cmd);
    PolymathOS.alert("Cache Cleaner", "System cache sweep complete. Freed up storage by removing obsolete cache entries and thumbnail temp files.");
    return "Cache cleanup successful";
})();
