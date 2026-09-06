// Polymath OS Real Engine: Direct System & Hardware Inspector
// Leverages POSIX CommonJS modules: 'os', 'fs', 'kernel', 'child_process'

(function() {
    console.time("system_inspection");
    PolymathOS.toast("Running OS Runtime System Inspection...");

    var os = require('os');
    var fs = require('fs');
    var kernel = require('kernel');
    var cp = require('child_process');

    // 1. Process and Hardware Platform
    var arch = os.arch();
    var platform = os.platform();
    var cpus = os.cpus();
    var totalMemMb = Math.round(os.totalmem() / 1048576);
    var freeMemMb = Math.round(os.freemem() / 1048576);
    var loadAverages = os.loadavg().join(", ");
    var uptimeHours = (os.uptime() / 3600).toFixed(2);

    // 2. Direct POSIX /proc read
    var procVersion = "N/A";
    try {
        if (fs.existsSync("/proc/version")) {
            procVersion = fs.readFileSync("/proc/version").trim();
        }
    } catch(e) {
        procVersion = e.message;
    }

    // 3. Kernel Subsystem Telemetry
    var kernelReport = kernel.getReport();
    var governor = (kernelReport.cpu && kernelReport.cpu.governor) ? kernelReport.cpu.governor : "N/A";
    var seLinux = kernelReport.isSeLinuxEnforcing ? "Enforcing" : "Permissive";

    // 4. Subprocess Execution via child_process
    var shellOutput = "";
    try {
        shellOutput = cp.execSync("uname -a || echo 'Linux Kernel Online'").trim();
    } catch (e) {
        shellOutput = e.message;
    }

    console.timeEnd("system_inspection");

    var summary = "=== POLYMATH REAL JS OS RUNTIME ===\n" +
                  "Architecture: " + arch + " (" + platform + ")\n" +
                  "CPU Cores: " + cpus.length + " | Governor: " + governor + "\n" +
                  "Physical RAM: " + freeMemMb + " MB free / " + totalMemMb + " MB total\n" +
                  "System Uptime: " + uptimeHours + " hrs | Load: [" + loadAverages + "]\n" +
                  "SELinux: " + seLinux + "\n\n" +
                  "=== KERNEL PROC SIGNATURE ===\n" + procVersion + "\n\n" +
                  "=== SUBPROCESS EXECUTION ===\n" + shellOutput;

    PolymathOS.alert("Polymath OS Telemetry", summary);
    return summary;
})();
