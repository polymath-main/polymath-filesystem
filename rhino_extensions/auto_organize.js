// auto_organize.js
// Polymath FileSystem Rhino JS Extension
// Automates organizing files in a given directory by extension.

// To use this, append polymath_std.js contents, or load it via the app.
// For demonstration, assuming FS object is available.

PolymathOS.toast("Running Auto-Organize script...");

// Use native shell command to organize
var cmd = `
dir="/sdcard/Download"
mkdir -p "$dir/Images" "$dir/Videos" "$dir/Documents"
mv "$dir"/*.jpg "$dir"/*.png "$dir"/*.jpeg "$dir"/Images/ 2>/dev/null
mv "$dir"/*.mp4 "$dir"/*.mkv "$dir"/Videos/ 2>/dev/null
mv "$dir"/*.pdf "$dir"/*.txt "$dir"/Documents/ 2>/dev/null
echo "Organization Complete"
`;

var res = PolymathOS.daemonCommand("execute_command", cmd);
PolymathOS.toast("Auto-Organize Complete!");
"Success";
