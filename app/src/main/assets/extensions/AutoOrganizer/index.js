// Polymath File System - AutoOrganizer Subsystem
// Automatically organizes downloaded files by extension into categorized directories.

(function() {
    var baseDir = "/storage/emulated/0/Download";
    var cmd = 'dir="' + baseDir + '"\n' +
        'mkdir -p "$dir/Images" "$dir/Videos" "$dir/Documents" "$dir/Audio" "$dir/Archives" "$dir/APKs"\n' +
        'mv "$dir"/*.jpg "$dir"/*.jpeg "$dir"/*.png "$dir"/*.gif "$dir"/*.webp "$dir/Images/" 2>/dev/null\n' +
        'mv "$dir"/*.mp4 "$dir"/*.mkv "$dir"/*.webm "$dir"/*.avi "$dir/Videos/" 2>/dev/null\n' +
        'mv "$dir"/*.pdf "$dir"/*.docx "$dir"/*.xlsx "$dir"/*.pptx "$dir"/*.txt "$dir/Documents/" 2>/dev/null\n' +
        'mv "$dir"/*.mp3 "$dir"/*.wav "$dir"/*.flac "$dir"/*.m4a "$dir/Audio/" 2>/dev/null\n' +
        'mv "$dir"/*.zip "$dir"/*.rar "$dir"/*.7z "$dir"/*.tar.gz "$dir/Archives/" 2>/dev/null\n' +
        'mv "$dir"/*.apk "$dir/APKs/" 2>/dev/null\n' +
        'echo "AutoOrganization finished for Download folder."';

    var res = PolymathOS.daemonCommand("execute_command", cmd);
    PolymathOS.toast("AutoOrganizer: Downloads organized");
    PolymathOS.alert("AutoOrganizer", "Successfully organized files in " + baseDir + " into Images, Videos, Documents, Audio, Archives, and APKs.");
    return "Organization complete for: " + baseDir;
})();
