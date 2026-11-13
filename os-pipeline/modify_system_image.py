"""
WanboLightOS - System Image In-Place Customizer
Applies debloat removals, injects WanboLauncher.apk, updates build.prop,
and sets SELinux contexts using debugfs.
"""
import os
import re
import subprocess
import sys

def run_debugfs(cmd: str, img_path: str, write: bool = False) -> str:
    """
    Run a debugfs command on the given ext4 image.
    @param cmd: Command string for debugfs.
    @param img_path: Absolute or relative path to ext4 image.
    @param write: Whether to open image with write permission (-w).
    @return: Standard output from debugfs.
    """
    args = ["debugfs"]
    if write:
        args.append("-w")
    args.extend(["-R", cmd, img_path])
    res = subprocess.run(args, capture_output=True, text=True)
    if res.returncode != 0 and "No such file or directory" not in res.stderr and "File not found" not in res.stderr:
        print(f"Warning on '{cmd}': {res.stderr.strip()}", file=sys.stderr)
    return res.stdout

def remove_ext4_recursive(remote_path: str, img_path: str):
    """
    Recursively remove a directory and its contents inside ext4 using debugfs.
    @param remote_path: Remote path in ext4 image to remove.
    @param img_path: Path to ext4 image file.
    """
    ls_out = run_debugfs(f"ls -l {remote_path}", img_path)
    lines = ls_out.splitlines()
    for line in lines:
        parts = line.split()
        if len(parts) >= 9:
            name = " ".join(parts[8:])
            if name in [".", ".."]:
                continue
            child_path = f"{remote_path}/{name}".replace("//", "/")
            mode = parts[1]
            if mode.startswith("4"): # Directory
                remove_ext4_recursive(child_path, img_path)
            else: # Regular file / link
                run_debugfs(f"rm {child_path}", img_path, write=True)
    run_debugfs(f"rmdir {remote_path}", img_path, write=True)

def apply_debloat(img_path: str):
    """
    Remove specified bloatware apps from /system/app and /system/priv-app.
    @param img_path: Path to ext4 image file.
    """
    apps_to_remove = [
        # /system/app bloatware (TyyLauncher preserved for Keystone & Projection Mode HAL)
        "/system/app/TV_OPEN_LAUNCHER_WHALE-zvt_mtk9255_p_prod",
        "/system/app/CleanCacheUtil",
        "/system/app/MemoryClean",
        "/system/app/Store_t_release_3.0.7_ZY_MTKWTV_3S",
        "/system/app/GeneralServices_ZY_MTKWTV_W_release_v2.0.9",
        "/system/app/dp-provider-Zhiyuan9255_2K_release_1.0.5",
        "/system/app/WFDSinkTest",
        "/system/app/PrintSpooler",
        "/system/app/EShareServer",
        "/system/app/EAirPlay",
        "/system/app/EDLNA",
        # /system/product/app bloatware
        "/system/product/app/MLocalMM2",
        # /system/priv-app bloatware
        "/system/priv-app/Tubesky",
        "/system/priv-app/LiveTVSetupWizard",
        "/system/priv-app/GoogleContactsSyncAdapter",
        "/system/priv-app/CalendarProvider",
        "/system/priv-app/ContactsProvider",
        "/system/priv-app/TvSettingsPlus",
        "/system/priv-app/com.android.tv.settings"
    ]
    print("[i] Applying debloat removals...")
    for target in apps_to_remove:
        print(f"  Removing: {target}")
        remove_ext4_recursive(target, img_path)

def inject_wanbo_launcher(apk_host_path: str, img_path: str):
    """
    Inject WanboLauncher.apk into /system/priv-app/WanboLauncher.
    @param apk_host_path: Host path to compiled WanboLauncher.apk.
    @param img_path: Path to ext4 image file.
    """
    print("[i] Injecting WanboLauncher into /system/priv-app/WanboLauncher/...")
    target_dir = "/system/priv-app/WanboLauncher"
    target_apk = f"{target_dir}/WanboLauncher.apk"
    
    # Create directory
    run_debugfs(f"mkdir {target_dir}", img_path, write=True)
    run_debugfs(f"ea_set {target_dir} security.selinux u:object_r:system_file:s0", img_path, write=True)
    run_debugfs(f"sif {target_dir} mode 040755", img_path, write=True)
    run_debugfs(f"sif {target_dir} uid 0", img_path, write=True)
    run_debugfs(f"sif {target_dir} gid 0", img_path, write=True)

    # Write APK
    run_debugfs(f"write {apk_host_path} {target_apk}", img_path, write=True)
    run_debugfs(f"ea_set {target_apk} security.selinux u:object_r:system_file:s0", img_path, write=True)
    run_debugfs(f"sif {target_apk} mode 0100644", img_path, write=True)
    run_debugfs(f"sif {target_apk} uid 0", img_path, write=True)
    run_debugfs(f"sif {target_apk} gid 0", img_path, write=True)
    print("  WanboLauncher.apk installed successfully.")

def inject_wanbo_settings(apk_host_path: str, img_path: str):
    """
    Inject WanboSettings.apk into /system/priv-app/WanboSettings.
    @param apk_host_path: Host path to compiled WanboSettings.apk.
    @param img_path: Path to ext4 image file.
    """
    print("[i] Injecting WanboSettings into /system/priv-app/WanboSettings/...")
    target_dir = "/system/priv-app/WanboSettings"
    target_apk = f"{target_dir}/WanboSettings.apk"
    
    # Create directory
    run_debugfs(f"mkdir {target_dir}", img_path, write=True)
    run_debugfs(f"ea_set {target_dir} security.selinux u:object_r:system_file:s0", img_path, write=True)
    run_debugfs(f"sif {target_dir} mode 040755", img_path, write=True)
    run_debugfs(f"sif {target_dir} uid 0", img_path, write=True)
    run_debugfs(f"sif {target_dir} gid 0", img_path, write=True)

    # Write APK
    run_debugfs(f"write {apk_host_path} {target_apk}", img_path, write=True)
    run_debugfs(f"ea_set {target_apk} security.selinux u:object_r:system_file:s0", img_path, write=True)
    run_debugfs(f"sif {target_apk} mode 0100644", img_path, write=True)
    run_debugfs(f"sif {target_apk} uid 0", img_path, write=True)
    run_debugfs(f"sif {target_apk} gid 0", img_path, write=True)
    print("  WanboSettings.apk installed successfully.")

def inject_material_files(apk_host_path: str, img_path: str):
    """
    Inject MaterialFiles.apk into /system/app/MaterialFiles.
    @param apk_host_path: Host path to MaterialFiles.apk.
    @param img_path: Path to ext4 image file.
    """
    print("[i] Injecting MaterialFiles into /system/app/MaterialFiles/...")
    target_dir = "/system/app/MaterialFiles"
    target_apk = f"{target_dir}/MaterialFiles.apk"
    
    # Create directory
    run_debugfs(f"mkdir {target_dir}", img_path, write=True)
    run_debugfs(f"ea_set {target_dir} security.selinux u:object_r:system_file:s0", img_path, write=True)
    run_debugfs(f"sif {target_dir} mode 040755", img_path, write=True)
    run_debugfs(f"sif {target_dir} uid 0", img_path, write=True)
    run_debugfs(f"sif {target_dir} gid 0", img_path, write=True)

    # Write APK
    run_debugfs(f"write {apk_host_path} {target_apk}", img_path, write=True)
    run_debugfs(f"ea_set {target_apk} security.selinux u:object_r:system_file:s0", img_path, write=True)
    run_debugfs(f"sif {target_apk} mode 0100644", img_path, write=True)
    run_debugfs(f"sif {target_apk} uid 0", img_path, write=True)
    run_debugfs(f"sif {target_apk} gid 0", img_path, write=True)
    print("  MaterialFiles.apk installed successfully.")

def update_build_prop(patch_file: str, img_path: str):
    """
    Update /system/build.prop with memory and hardware acceleration settings.
    @param patch_file: Path to build.prop.patch file.
    @param img_path: Path to ext4 image file.
    """
    print("[i] Patching /system/build.prop...")
    tmp_orig = "/tmp/orig_build.prop"
    tmp_new = "/tmp/new_build.prop"
    
    run_debugfs(f"dump /system/build.prop {tmp_orig}", img_path)
    
    with open(tmp_orig, "r", encoding="utf-8", errors="replace") as f:
        orig_content = f.read()

    with open(patch_file, "r", encoding="utf-8") as f:
        patch_content = f.read()

    # Parse patches
    patch_lines = [l.strip() for l in patch_content.splitlines() if l.strip() and not l.strip().startswith("#")]
    patch_dict = {}
    for line in patch_lines:
        if "=" in line:
            k, v = line.split("=", 1)
            patch_dict[k.strip()] = v.strip()

    # Apply in-place replacements or append
    orig_lines = orig_content.splitlines()
    final_lines = []
    seen_keys = set()
    for line in orig_lines:
        s = line.strip()
        if "=" in s and not s.startswith("#"):
            k, v = s.split("=", 1)
            k = k.strip()
            if k in patch_dict:
                final_lines.append(f"{k}={patch_dict[k]}")
                seen_keys.add(k)
                continue
        final_lines.append(line)

    final_lines.append("\n# Added by WanboLightOS Optimization Patch")
    for k, v in patch_dict.items():
        if k not in seen_keys:
            final_lines.append(f"{k}={v}")

    new_content = "\n".join(final_lines) + "\n"
    with open(tmp_new, "w", encoding="utf-8") as f:
        f.write(new_content)

    # Write back to ext4
    run_debugfs("rm /system/build.prop", img_path, write=True)
    run_debugfs(f"write {tmp_new} /system/build.prop", img_path, write=True)
    run_debugfs("ea_set /system/build.prop security.selinux u:object_r:system_file:s0", img_path, write=True)
    run_debugfs("sif /system/build.prop mode 0100644", img_path, write=True)
    run_debugfs("sif /system/build.prop uid 0", img_path, write=True)
    run_debugfs("sif /system/build.prop gid 0", img_path, write=True)
    print("  /system/build.prop updated and patched successfully.")

def main():
    """
    Main modification entrypoint.
    """
    img_path = "/mnt/c/Users/fecam/wanbo-light-os/build/test_system_raw.img"
    apk_path = "/mnt/c/Users/fecam/wanbo-light-os/WanboLauncher.apk"
    settings_apk_path = "/mnt/c/Users/fecam/wanbo-light-os/WanboSettings.apk"
    material_apk_path = "/mnt/c/Users/fecam/wanbo-light-os/build/MaterialFiles.apk"
    patch_path = "/mnt/c/Users/fecam/wanbo-light-os/os-pipeline/patches/build.prop.patch"

    if not os.path.exists(img_path):
        print(f"Error: image {img_path} not found.")
        sys.exit(1)

    apply_debloat(img_path)
    if os.path.exists(apk_path):
        inject_wanbo_launcher(apk_path, img_path)
    if os.path.exists(settings_apk_path):
        inject_wanbo_settings(settings_apk_path, img_path)
    if os.path.exists(material_apk_path):
        inject_material_files(material_apk_path, img_path)
    update_build_prop(patch_path, img_path)

    print("[i] Checking filesystem integrity with e2fsck...")
    res = subprocess.run(["e2fsck", "-f", "-y", img_path], capture_output=True, text=True)
    print(f"e2fsck exit code: {res.returncode}")
    print(res.stdout[-300:] if len(res.stdout) > 300 else res.stdout)
    print("[i] System image customization completed successfully.")

if __name__ == "__main__":
    main()
