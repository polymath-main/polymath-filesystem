var smbConfig = {
    host: "",
    port: 445,
    user: "guest",
    pass: "",
    path: "/"
};

function startSmbManager() {
    PolymathOS.prompt("Enter SMB Host", "smbOnHostEntered");
}

function smbOnHostEntered(host) {
    if (host) {
        smbConfig.host = host;
        PolymathOS.prompt("Enter SMB Port (default 445)", "smbOnPortEntered");
    } else {
        PolymathOS.toast("Host cannot be empty. Operation cancelled.");
    }
}

function smbOnPortEntered(port) {
    smbConfig.port = parseInt(port) || 445;
    PolymathOS.prompt("Enter SMB Username", "smbOnUserEntered");
}

function smbOnUserEntered(user) {
    smbConfig.user = user || "guest";
    PolymathOS.prompt("Enter SMB Password", "smbOnPassEntered");
}

function smbOnPassEntered(pass) {
    smbConfig.pass = pass || "";
    PolymathOS.toast("Connecting to SMB " + smbConfig.host + "...");
    try {
        if (typeof PolymathOS.smbRequest === "function") {
            PolymathOS.smbRequest(smbConfig.host, smbConfig.port, smbConfig.user, smbConfig.pass, "LIST", smbConfig.path);
            PolymathOS.alert("SMB request executed successfully for " + smbConfig.host);
        } else {
            PolymathOS.alert("SMB connection attempted to " + smbConfig.host + " (smbRequest not found in API)");
        }
    } catch (e) {
        PolymathOS.alert("Error executing SMB request: " + e.message);
    }
}

// Start the sequence
startSmbManager();
