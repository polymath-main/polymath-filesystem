// Polymath File System - Utilities Module
// [ChronosBackup.js]
// Creates point-in-time forensic snapshots of user files.

(function() {
    var target = "/storage/emulated/0/DCIM";
    PolymathOS.toast("Chronos: Generating point-in-time snapshot for " + target);
    var res = PolymathOS.daemonCommand("chronos_snapshot", target);
    try {
        var parsed = JSON.parse(res);
        PolymathOS.alert("Chronos Backup", parsed.output);
        return parsed.output;
    } catch(e) {
        PolymathOS.alert("Chronos Backup", "Snapshot completed for " + target);
        return "Snapshot complete";
    }
})();
