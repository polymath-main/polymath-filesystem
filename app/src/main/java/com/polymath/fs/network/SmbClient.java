package com.polymath.fs.network;

/**
 * Legacy SMB client stub.
 *
 * @deprecated Legacy SMB access is deprecated. Modern file access uses
 *             SftpVfsProvider and the Unified VFS Provider architecture.
 */
@Deprecated
public class SmbClient {
    @Deprecated
    public static String execute(String host, int port, String user, String pass, String command, String path) {
        return "SMB Native Support Pending. Modern file access uses SftpVfsProvider and the Unified VFS Provider architecture.";
    }
}
