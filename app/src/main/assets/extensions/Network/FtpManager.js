var ftpConfig = {
    host: "",
    port: 21,
    user: "anonymous",
    pass: "",
    path: "/"
};

function startFtpManager() {
    PolymathOS.prompt("Enter FTP Host", "ftpOnHostEntered");
}

function ftpOnHostEntered(host) {
    if (host) {
        ftpConfig.host = host;
        PolymathOS.prompt("Enter FTP Port (default 21)", "ftpOnPortEntered");
    } else {
        PolymathOS.toast("Host cannot be empty. Operation cancelled.");
    }
}

function ftpOnPortEntered(port) {
    ftpConfig.port = parseInt(port) || 21;
    PolymathOS.prompt("Enter FTP Username", "ftpOnUserEntered");
}

function ftpOnUserEntered(user) {
    ftpConfig.user = user || "anonymous";
    PolymathOS.prompt("Enter FTP Password", "ftpOnPassEntered");
}

function ftpOnPassEntered(pass) {
    ftpConfig.pass = pass || "";
    PolymathOS.toast("Connecting to " + ftpConfig.host + "...");
    try {
        PolymathOS.ftpRequest(ftpConfig.host, ftpConfig.port, ftpConfig.user, ftpConfig.pass, "LIST", ftpConfig.path);
        PolymathOS.alert("FTP request executed successfully for " + ftpConfig.host);
    } catch (e) {
        PolymathOS.alert("Error executing FTP request: " + e.message);
    }
}

// Start the sequence
startFtpManager();
