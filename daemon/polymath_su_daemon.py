import socket
import json
import subprocess
import os
import stat
import threading
import time
import shutil
import ftplib
import hashlib

PORT = 50505
HOST = '127.0.0.1'

def detect_su_binary():
    binaries = ['magisk su', 'ksu', 'su', 'tsu']
    for b in binaries:
        try:
            cmd = b.split() + ['-c', 'echo 1']
            result = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=2)
            if result.returncode == 0: return b.split()
        except Exception: continue
    return None

def execute_root_command(su_cmd, target_command):
    cmd = su_cmd + ['-c', target_command]
    try:
        result = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, timeout=15)
        return {"success": result.returncode == 0, "stdout": result.stdout, "stderr": result.stderr}
    except Exception as e:
        return {"success": False, "error": str(e)}

def parse_ls_output(stdout, path):
    files = []
    lines = stdout.strip().split('\n')
    for line in lines:
        if not line.strip(): continue
        parts = line.split(maxsplit=8)
        if len(parts) >= 9:
            perms = parts[0]
            size = parts[4]
            name = parts[8]
            is_dir = perms.startswith('d')
            files.append({"name": name, "isDirectory": is_dir, "size": int(size) if size.isdigit() else 0, "permissions": perms, "uri": os.path.join(path, name)})
    return files

# Advanced Automation & Ephemeral States
tracked_directories = {}
ghost_vaults = {}

def automation_worker(su_cmd):
    while True:
        # Standard Automation
        for directory, config in list(tracked_directories.items()):
            try:
                res = execute_root_command(su_cmd, f"ls '{directory}'")
                if res["success"]:
                    files = res["stdout"].strip().split('\n')
                    for f in files:
                        if not f: continue
                        ext = f.split('.')[-1].lower() if '.' in f else ''
                        if ext in ['jpg', 'png', 'jpeg', 'gif']: execute_root_command(su_cmd, f"mkdir -p '{directory}/Images' && mv '{directory}/{f}' '{directory}/Images/'")
                        elif ext in ['mp4', 'mkv']: execute_root_command(su_cmd, f"mkdir -p '{directory}/Videos' && mv '{directory}/{f}' '{directory}/Videos/'")
                        elif ext in ['pdf', 'txt']: execute_root_command(su_cmd, f"mkdir -p '{directory}/Documents' && mv '{directory}/{f}' '{directory}/Documents/'")
            except Exception: pass
            
        # Ghost Vault (Forensic Shredding after 1 minute for demo)
        for vault, expiry in list(ghost_vaults.items()):
            try:
                res = execute_root_command(su_cmd, f"find '{vault}' -type f -mmin +1")
                if res["success"] and res["stdout"].strip():
                    files_to_shred = res["stdout"].strip().split('\n')
                    for f in files_to_shred:
                        # DoD 5220.22-M Style Wipe (Random Data Overwrite before deletion)
                        execute_root_command(su_cmd, f"dd if=/dev/urandom of='{f}' bs=1M count=10; rm -f '{f}'")
            except Exception: pass
            
        time.sleep(5)

def handle_request(su_cmd, request_data):
    action = request_data.get("action")
    path = request_data.get("path")

    if action == "list_dir":
        res = execute_root_command(su_cmd, f"ls -lA '{path}'")
        if res["success"]: return {"success": True, "files": parse_ls_output(res["stdout"], path)}
        return res

    elif action == "search_files":
        query = request_data.get("query", "")
        cmd_str = f"find '{path}' -iname '*{query}*' -print0 | xargs -0 ls -lAd 2>/dev/null | head -n 100"
        res = execute_root_command(su_cmd, cmd_str)
        if res["success"]: return {"success": True, "files": parse_ls_output(res["stdout"], path)}
        return res

    # 1. Format Cloaking (Header Scrambling for File Hiding)
    elif action == "format_cloak":
        try:
            # Scramble the first 32 bytes (Magic Header) via XOR to make the file unreadable to the OS.
            # Re-running the command unscrambles it.
            with open(path, 'r+b') as f:
                header = bytearray(f.read(32))
                for i in range(len(header)):
                    header[i] ^= 0x5A  # XOR cipher byte
                f.seek(0)
                f.write(header)
            return {"success": True, "message": "File Header Cloaked/Uncloaked successfully."}
        except Exception as e:
            return {"success": False, "error": str(e)}

    # 2. Hardlink Deduplication (Zero-Space Duplicates)
    elif action == "hardlink_dedup":
        # Find exact matches, delete the duplicate, and recreate it as a hardlink to the original
        # This saves physical disk space while keeping the file visible in both locations
        script = f"""
        find '{path}' -type f -exec md5sum {{}} + | sort | awk 'BEGIN{{prev=""}} {{if ($1 == prev) print "rm \\""$2"\\"; ln \\""prevFile"\\" \\""$2"\\""; else {{prev=$1; prevFile=$2}}}}' | sh
        """
        return execute_root_command(su_cmd, script)

    # 3. Time-Travel Snapshots (Chronos)
    elif action == "chronos_snapshot":
        timestamp = int(time.time())
        dest = os.path.join(path, f".chronos_{timestamp}.tar.gz")
        return execute_root_command(su_cmd, f"tar -czf '{dest}' --exclude='*.tar.gz' -C '{path}' .")
        
    elif action == "chronos_restore":
        archive = request_data.get("archive")
        return execute_root_command(su_cmd, f"tar -xzf '{archive}' -C '{path}'")

    # 4. Forensic Auto-Shredding (Ghost Vault)
    elif action == "ghost_vault":
        execute_root_command(su_cmd, f"mkdir -p '{path}/GhostVault'")
        vault_path = f"{path}/GhostVault"
        ghost_vaults[vault_path] = True
        return {"success": True, "message": "Ghost Vault activated. Files here will be forensically shredded after 60 seconds."}

    # 5. Ghost Mounting (RAM-Disk Injection)
    elif action == "mount_ramdisk":
        mount_pt = os.path.join(path, "HyperDrive")
        execute_root_command(su_cmd, f"mkdir -p '{mount_pt}'")
        res = execute_root_command(su_cmd, f"mount -t tmpfs -o size=512m tmpfs '{mount_pt}'")
        if res["success"]:
            return {"success": True, "message": "Mounted 512MB RAM-Disk at HyperDrive."}
        return res

    elif action == "delete_file": return execute_root_command(su_cmd, f"rm -rf '{path}'")
    elif action == "archive":
        target = f"{path}.tar.gz"
        parent = os.path.dirname(path)
        base = os.path.basename(path)
        return execute_root_command(su_cmd, f"tar -czf '{target}' -C '{parent}' '{base}'")
    elif action == "execute_command":
        cmd = request_data.get("command", "")
        return execute_root_command(su_cmd, cmd)
    elif action == "enable_automation":
        tracked_directories[path] = True
        return {"success": True, "message": f"Started watching {path} for auto-organization"}
    
    return {"success": False, "error": "Unknown action"}

def start_daemon():
    su_cmd = detect_su_binary()
    if not su_cmd: return
    threading.Thread(target=automation_worker, args=(su_cmd,), daemon=True).start()

    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        s.bind((HOST, PORT))
        s.listen()
        while True:
            conn, addr = s.accept()
            with conn:
                try:
                    data = conn.recv(8192)
                    if data:
                        req = json.loads(data.decode('utf-8'))
                        res = handle_request(su_cmd, req)
                        conn.sendall(json.dumps(res).encode('utf-8'))
                except Exception: pass

if __name__ == "__main__":
    start_daemon()
