const path = PolymathOS.prompt("Enter file path to shred:");
if (path) {
    PolymathOS.daemonCommand("execute_command", `dd if=/dev/urandom of="${path}" bs=1M count=10 && rm -f "${path}"`);
    console.log(`Shredded ${path}`);
}
