// Polymath OS Real Engine: Network Interfaces & HTTP Fetch Probe
// Leverages POSIX 'os' module and unrestricted global 'fetch'

(function() {
    PolymathOS.toast("Probing Network Interfaces & HTTP Gateway...");

    var os = require('os');
    var ifaces = os.networkInterfaces();
    var ifaceSummary = [];

    for (var name in ifaces) {
        if (ifaces.hasOwnProperty(name)) {
            var addrs = ifaces[name];
            var ipList = addrs.map(function(a) { return a.address + " (" + a.family + ")"; }).join(", ");
            ifaceSummary.push(name + ": " + ipList);
        }
    }

    var ifaceText = ifaceSummary.length > 0 ? ifaceSummary.join("\n") : "No active network adapters";

    // Perform live HTTP fetch
    var httpReport = "Testing Network Reachability...";
    try {
        var resp = fetch("https://httpbin.org/get", {
            method: "GET",
            headers: {
                "User-Agent": "Polymath-OS-Runtime/2.0"
            }
        });
        httpReport = "Status: " + resp.status + " " + resp.statusText + " (OK=" + resp.ok + ")";
    } catch(e) {
        httpReport = "HTTP Fetch failed: " + e.message;
    }

    var output = "=== ACTIVE NETWORK INTERFACES ===\n" +
                 ifaceText + "\n\n" +
                 "=== LIVE HTTP STATUS ===\n" +
                 httpReport;

    PolymathOS.alert("Network Probe", output);
    return output;
})();
