package com.polymath.fs.core;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RootEngine {

    private static String suBinary = null;
    private static final Set<String> ghostVaults = new HashSet<>();

    static {
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(5000);
                    for (String vault : new ArrayList<>(ghostVaults)) {
                        // Find files older than 1 minute
                        String findCmd = "find '" + vault + "' -type f -mmin +1";
                        JSONObject findRes = executeCommand(findCmd);
                        if (findRes.optBoolean("success")) {
                            String out = findRes.optString("stdout", "").trim();
                            if (!out.isEmpty()) {
                                String[] files = out.split("\n");
                                for (String f : files) {
                                    // DoD 5220.22-M Style Wipe (Random Data Overwrite before deletion)
                                    executeCommand("dd if=/dev/urandom of='" + f + "' bs=1M count=10; rm -f '" + f + "'");
                                }
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }
        }).start();
    }

    private static String getSuBinary() {
        if (suBinary != null) return suBinary;
        String[] binaries = {"magisk su", "ksu", "su", "tsu", "sh"};
        for (String b : binaries) {
            try {
                Process process = Runtime.getRuntime().exec(b.split(" ")[0] + " -c echo 1");
                process.waitFor();
                if (process.exitValue() == 0) {
                    suBinary = b;
                    return suBinary;
                }
            } catch (Exception ignored) {}
        }
        suBinary = "sh";
        return suBinary;
    }

    public static JSONObject executeCommand(String command) {
        JSONObject result = new JSONObject();
        try {
            String su = getSuBinary();
            String[] cmdArray;
            if (su.contains(" ")) {
                cmdArray = new String[]{su.split(" ")[0], su.split(" ")[1], "-c", command};
            } else {
                cmdArray = new String[]{su, "-c", command};
            }
            
            Process process = Runtime.getRuntime().exec(cmdArray);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            
            StringBuilder errorOutput = new StringBuilder();
            while ((line = errorReader.readLine()) != null) {
                errorOutput.append(line).append("\n");
            }
            
            process.waitFor();
            
            result.put("success", process.exitValue() == 0);
            result.put("stdout", output.toString());
            result.put("stderr", errorOutput.toString());
        } catch (Exception e) {
            try {
                result.put("success", false);
                result.put("error", e.getMessage());
            } catch (Exception ignored) {}
        }
        return result;
    }

    private static JSONArray parseLsOutput(String stdout, String path) throws Exception {
        JSONArray files = new JSONArray();
        String[] lines = stdout.trim().split("\n");
        for (String line : lines) {
            if (line.trim().isEmpty()) continue;
            String[] parts = line.split("\\s+", 9);
            if (parts.length >= 9) {
                String perms = parts[0];
                String size = parts[4];
                String name = parts[8];
                boolean isDir = perms.startsWith("d");
                
                JSONObject fileObj = new JSONObject();
                fileObj.put("name", name);
                fileObj.put("isDirectory", isDir);
                try {
                    fileObj.put("size", Long.parseLong(size));
                } catch (Exception e) {
                    fileObj.put("size", 0);
                }
                fileObj.put("permissions", perms);
                fileObj.put("uri", path.endsWith("/") ? path + name : path + "/" + name);
                files.put(fileObj);
            }
        }
        return files;
    }

    public static JSONObject executeAction(JSONObject requestData) {
        JSONObject res = new JSONObject();
        try {
            String action = requestData.optString("action", "");
            String path = requestData.optString("path", "");

            if (action.equals("list_dir")) {
                JSONObject cmdRes = executeCommand("ls -lA '" + path + "'");
                if (cmdRes.optBoolean("success")) {
                    res.put("success", true);
                    res.put("files", parseLsOutput(cmdRes.optString("stdout", ""), path));
                } else {
                    return cmdRes;
                }
                return res;
            }
            
            if (action.equals("search_files")) {
                String query = requestData.optString("query", "");
                String cmd = "find '" + path + "' -iname '*" + query + "*' -print0 | xargs -0 ls -lAd 2>/dev/null | head -n 100";
                JSONObject cmdRes = executeCommand(cmd);
                if (cmdRes.optBoolean("success")) {
                    res.put("success", true);
                    res.put("files", parseLsOutput(cmdRes.optString("stdout", ""), path));
                } else {
                    return cmdRes;
                }
                return res;
            }

            if (action.equals("format_cloak")) {
                try {
                    File file = new File(path);
                    RandomAccessFile raf = new RandomAccessFile(file, "rw");
                    byte[] header = new byte[32];
                    int read = raf.read(header);
                    if (read > 0) {
                        for (int i = 0; i < read; i++) {
                            header[i] ^= 0x5A; // XOR scramble
                        }
                        raf.seek(0);
                        raf.write(header, 0, read);
                    }
                    raf.close();
                    res.put("success", true);
                    res.put("message", "File Header Cloaked/Uncloaked successfully.");
                } catch (Exception e) {
                    res.put("success", false);
                    res.put("error", e.getMessage());
                }
                return res;
            }

            if (action.equals("hardlink_dedup")) {
                String cmd = "find '" + path + "' -type f -exec md5sum {} + | sort | awk 'BEGIN{prev=\"\"} {if ($1 == prev) print \"rm \\\"\"$2\"\\\"; ln \\\"\"prevFile\"\\\" \\\"\"$2\"\\\"\"; else {prev=$1; prevFile=$2}}' | sh";
                return executeCommand(cmd);
            }

            if (action.equals("chronos_snapshot")) {
                long timestamp = System.currentTimeMillis() / 1000;
                String dest = new File(path, ".chronos_" + timestamp + ".tar.gz").getAbsolutePath();
                return executeCommand("tar -czf '" + dest + "' --exclude='*.tar.gz' -C '" + path + "' .");
            }

            if (action.equals("chronos_restore")) {
                String archive = requestData.optString("archive", "");
                return executeCommand("tar -xzf '" + archive + "' -C '" + path + "'");
            }

            if (action.equals("ghost_vault")) {
                String vaultPath = path + "/GhostVault";
                executeCommand("mkdir -p '" + vaultPath + "'");
                ghostVaults.add(vaultPath);
                res.put("success", true);
                res.put("message", "Ghost Vault activated natively. Background thread will shred files after 60 seconds.");
                return res;
            }

            if (action.equals("mount_ramdisk")) {
                String mountPt = new File(path, "HyperDrive").getAbsolutePath();
                executeCommand("mkdir -p '" + mountPt + "'");
                JSONObject cmdRes = executeCommand("mount -t tmpfs -o size=512m tmpfs '" + mountPt + "'");
                if (cmdRes.optBoolean("success")) {
                    res.put("success", true);
                    res.put("message", "Mounted 512MB RAM-Disk at HyperDrive.");
                    return res;
                }
                return cmdRes;
            }

            if (action.equals("delete_file")) {
                return executeCommand("rm -rf '" + path + "'");
            }

            if (action.equals("archive")) {
                String target = path + ".tar.gz";
                String parent = new File(path).getParent();
                String base = new File(path).getName();
                return executeCommand("tar -czf '" + target + "' -C '" + parent + "' '" + base + "'");
            }

            if (action.equals("execute_command")) {
                return executeCommand(requestData.optString("command", ""));
            }

            res.put("success", false);
            res.put("error", "Unknown action");

        } catch (Exception e) {
            try {
                res.put("success", false);
                res.put("error", e.getMessage());
            } catch (Exception ignored) {}
        }
        return res;
    }
}
