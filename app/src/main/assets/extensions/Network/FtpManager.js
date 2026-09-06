// Polymath File System - Network Module
// [FtpManager.js] FTP Client Connection & Endpoint Validator

var ftpConfig = {
    host: "127.0.0.1",
    port: 21,
    user: "anonymous",
    pass: "",
    path: "/"
};

function startFtpManager() {
    var host = PolymathOS.prompt("Enter FTP Host (e.g. 192.168.1.100 or 127.0.0.1)", "ftpOnHostEntered");
    if (host) {
        ftpConfig.host = host;
    }
    PolymathOS.toast("Validating FTP endpoint: " + ftpConfig.host + ":" + ftpConfig.port);
    try {
        var res = PolymathOS.ftpRequest(ftpConfig.host, ftpConfig.port, ftpConfig.user, ftpConfig.pass, "LIST", ftpConfig.path);
        var parsed = JSON.parse(res);
        PolymathOS.alert("FTP Manager", parsed.status || "FTP Status received");
    } catch (e) {
        PolymathOS.alert("FTP Error", "Failed to connect to FTP: " + e.message);
    }
}

function ftpOnHostEntered(host) {
    if (host) ftpConfig.host = host;
}

startFtpManager();
"FTP Manager: Checked connection to " + ftpConfig.host + ":" + ftpConfig.port;
