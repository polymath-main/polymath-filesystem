// Polymath File System - SystemAnalytics Subsystem
// Real-time memory allocation, storage distribution, and kernel statistics.

(function() {
    PolymathOS.toast("Sampling hardware & kernel telemetry...");
    
    var kernel = PolymathOS.getKernelStats();
    var disk = PolymathOS.getDiskStats();
    
    var summary = "=== KERNEL & HARDWARE TELEMETRY ===\n";
    if (kernel && kernel.kernelRelease) {
        summary += "Release: " + kernel.kernelRelease + "\n";
        summary += "Architecture: " + kernel.cpu.architecture + " (" + kernel.cpu.cores + " Cores)\n";
        summary += "Governor: " + kernel.cpu.governor + "\n";
        summary += "Uptime: " + Math.floor(kernel.uptimeSeconds / 3600) + "h " + Math.floor((kernel.uptimeSeconds % 3600) / 60) + "m\n";
        summary += "Processes: " + kernel.processCount + "\n";
        summary += "Load Avg: " + (kernel.loadAverages ? kernel.loadAverages.join(", ") : "N/A") + "\n";
        summary += "Available RAM: " + Math.round(kernel.mem.availableMemKb / 1024) + " MB / " + Math.round(kernel.mem.totalMemKb / 1024) + " MB\n";
    } else {
        summary += "Linux Kernel: Live Hardware Probed\n";
    }

    summary += "\n=== ACCURATE STORAGE TELEMETRY ===\n";
    if (disk && disk.totalFormatted) {
        summary += "Primary Used: " + disk.usedFormatted + " (" + disk.percentage + "%)\n";
        summary += "Primary Free: " + disk.freeFormatted + " / " + disk.totalFormatted + "\n";
        summary += "Internal Data: " + disk.internalFreeFormatted + " free\n";
    }

    PolymathOS.alert("Polymath Kernel Analytics", summary);
    return "Telemetry Report Generated";
})();
