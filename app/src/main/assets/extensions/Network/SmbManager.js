// Polymath File System - Network Module
// [SmbManager.js] SMB / CIFS Network Share Connection Manager

var smbConfig = {
    host: "127.0.0.1",
    port: 445,
    user: "guest",
    pass: "",
    path: "/"
};

function startSmbManager() {
    var host = PolymathOS.prompt("Enter SMB Host (e.g. 192.168.1.50 or 127.0.0.1)", "smbOnHostEntered");
    if (host) {
        smbConfig.host = host;
    }
    PolymathOS.toast("Connecting to SMB share at " + smbConfig.host + ":" + smbConfig.port);
    try {
        var res = PolymathOS.smbRequest(smbConfig.host, smbConfig.port, smbConfig.user, smbConfig.pass, "LIST", smbConfig.path);
        var parsed = JSON.parse(res);
        PolymathOS.alert("SMB Manager", parsed.status || "SMB Status received");
    } catch (e) {
        PolymathOS.alert("SMB Error", "Error executing SMB request: " + e.message);
    }
}

function smbOnHostEntered(host) {
    if (host) smbConfig.host = host;
}

startSmbManager();
"SMB Manager: Queried " + smbConfig.host + ":" + smbConfig.port;
