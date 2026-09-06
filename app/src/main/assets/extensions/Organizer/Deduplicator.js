// Polymath File System - Organizer Module
// [Deduplicator.js]
// Identifies and organizes duplicate files across storage partitions.

(function() {
    PolymathOS.toast("Deduplicator: Scanning storage tree for duplicate files...");
    var target = "/storage/emulated/0/Download";
    var res = PolymathOS.daemonCommand("hardlink_dedup", target);
    try {
        var parsed = JSON.parse(res);
        PolymathOS.alert("Deduplicator Results", parsed.output);
        return parsed.output;
    } catch(e) {
        PolymathOS.alert("Deduplicator", "Deduplication sweep finished for " + target);
        return "Scan complete";
    }
})();
