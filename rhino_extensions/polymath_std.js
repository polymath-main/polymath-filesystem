/**
 * Polymath Standard Library for Rhino JS Extensions
 * Provides a clean wrapper around the PolymathOS Java Bridge.
 */

var FS = {
    toast: function(msg) {
        PolymathOS.toast(msg);
    },
    
    readFile: function(path) {
        return PolymathOS.readFile(path);
    },
    
    execute: function(cmd) {
        var res = PolymathOS.daemonCommand("execute_command", cmd);
        try {
            return JSON.parse(res);
        } catch (e) {
            return {success: false, error: res};
        }
    },
    
    mkdir: function(path) {
        return this.execute("mkdir -p '" + path + "'");
    },
    
    move: function(src, dest) {
        return this.execute("mv '" + src + "' '" + dest + "'");
    },
    
    remove: function(path) {
        return PolymathOS.daemonCommand("delete_file", path);
    },
    
    archive: function(path) {
        return PolymathOS.daemonCommand("archive", path);
    },
    
    // Polymath Core Operations
    cloak: function(path) {
        return PolymathOS.daemonCommand("format_cloak", path);
    },
    
    ghostVault: function(path) {
        return PolymathOS.daemonCommand("ghost_vault", path);
    }
};
