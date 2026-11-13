"""
Searches mounted system image for Netflix APKs and packages.
"""
import os
import subprocess

def main():
    """
    Main entry point for scanning system partition APKs for Netflix.

    @returns {None}
    """
    subprocess.run(['mkdir', '-p', '/tmp/sys_mnt'])
    subprocess.run(['mount', '-o', 'loop', '/mnt/c/Users/fecam/wanbo-light-os/build/test_system_raw.img', '/tmp/sys_mnt'])

    try:
        for root, dirs, files in os.walk('/tmp/sys_mnt'):
            for f in files:
                if f.endswith('.apk'):
                    apk_path = os.path.join(root, f)
                    try:
                        out = subprocess.check_output(['aapt', 'dump', 'badging', apk_path], stderr=subprocess.DEVNULL).decode('utf-8', errors='replace')
                        if 'netflix' in out.lower():
                            print('Found netflix in:', apk_path)
                            for line in out.splitlines():
                                if 'package:' in line or 'application-label:' in line:
                                    print('  ', line)
                    except:
                        pass
    finally:
        subprocess.run(['umount', '/tmp/sys_mnt'])
        subprocess.run(['rm', '-rf', '/tmp/sys_mnt'])

if __name__ == '__main__':
    main()
