// AutoArchive.js
function onNewDownload(event) {
    if (event && event.file && event.file.endsWith(".log")) {
        PolymathOS.daemonCommand("archive", event.file);
    }
}

PolymathOS.listen("FILE_CREATED", "/sdcard/Download", "onNewDownload");
