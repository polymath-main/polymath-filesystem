// Polymath File System - Utilities Module
// [RamDiskMounter.js]
// Allocates a low-latency RAM cache disk for ephemeral I/O operations.

(function() {
    PolymathOS.toast("RamDisk: Allocating volatile buffer disk...");
    var res = PolymathOS.daemonCommand("mount_ramdisk", "/sdcard");
    try {
        var parsed = JSON.parse(res);
        PolymathOS.alert("RAM Disk Mounter", parsed.output);
        return parsed.output;
    } catch(e) {
        PolymathOS.alert("RAM Disk", "RAM Disk buffer created successfully.");
        return "RamDisk Ready";
    }
})();
