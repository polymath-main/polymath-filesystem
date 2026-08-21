// AutoOrganizer Subsystem

var cmd = `
dir="/sdcard/Download"
mkdir -p "$dir/Images" "$dir/Videos" "$dir/Documents"
mv "$dir"/*.jpg "$dir"/*.png "$dir"/*.jpeg "$dir"/Images/ 2>/dev/null
mv "$dir"/*.mp4 "$dir"/*.mkv "$dir"/Videos/ 2>/dev/null
mv "$dir"/*.pdf "$dir"/*.txt "$dir"/Documents/ 2>/dev/null
echo "Organization Complete"
`;

var res = PolymathOS.daemonCommand("execute_command", cmd);
PolymathOS.alert("AutoOrganizer", "Successfully sorted Downloads into Images, Videos, and Documents.");
"Success";
