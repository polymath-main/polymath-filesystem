// SystemAnalytics Subsystem

var mem = PolymathOS.daemonCommand("execute_command", "free -h | grep Mem | awk '{print $3 \" / \" $2}'");
var disk = PolymathOS.daemonCommand("execute_command", "df -h /sdcard | tail -1 | awk '{print $4 \" Free\"}'");

var json = JSON.parse(mem);
var jsonDisk = JSON.parse(disk);

PolymathOS.alert("System Analytics", "RAM Usage: " + json.output.trim() + "\nStorage: " + jsonDisk.output.trim());
"Analytics Generated";
