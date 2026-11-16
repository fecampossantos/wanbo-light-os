"""
Verify the assembled WanboLightOS firmware binary.
"""
import os
import struct
import subprocess
import zlib

def main():
    """
    Main entry point for firmware verification.

    @returns {None}
    """
    repo_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    fn = os.path.join(repo_root, "build", "WanboLightOS_v1.0.bin")
    total_sz = os.path.getsize(fn)
    print(f"[i] Checking firmware: {fn} ({total_sz:,} bytes)")

    # 1. Check footer
    with open(fn, "rb") as f:
        f.seek(total_sz - 32)
        footer = f.read(32)
        magic = footer[:8]
        header_crc_f = struct.unpack("<I", footer[8:12])[0]
        crc2_f = struct.unpack("<I", footer[12:16])[0]
        first16 = footer[16:32]

        print("  Footer Magic:", magic)
        print(f"  Footer Header CRC: 0x{header_crc_f:08x}")
        print(f"  Footer CRC2:       0x{crc2_f:08x}")
        print("  Footer First 16:", first16)

        # Recalculate header CRC
        f.seek(0)
        hdr = f.read(16384)
        cal_hdr_crc = zlib.crc32(hdr) & 0xffffffff
        print(f"  Recalculated Header CRC: 0x{cal_hdr_crc:08x} (matches: {cal_hdr_crc == header_crc_f})")
        assert cal_hdr_crc == header_crc_f

        # Recalculate CRC2
        f.seek(0)
        crc = 0
        rem = total_sz - 20
        while rem > 0:
            chunk = f.read(min(rem, 8 * 1024 * 1024))
            crc = zlib.crc32(chunk, crc)
            rem -= len(chunk)
        cal_crc2 = crc & 0xffffffff
        print(f"  Recalculated CRC2:       0x{cal_crc2:08x} (matches: {cal_crc2 == crc2_f})")
        assert cal_crc2 == crc2_f

    # 2. Extract and decode all 10 chunks from the assembled bin
    print("[i] Unsparsing system partition from assembled firmware binary...")
    offsets = [
        0x1130e000, 0x1770e000, 0x1db0e000, 0x23f0e000, 0x2a30e000,
        0x3070e000, 0x36b0e000, 0x3cf0e000, 0x4330e000, 0x4970e000
    ]
    verify_raw = os.path.join(repo_root, "build", "verify_assembled_raw.img")
    with open(fn, "rb") as fin, open(verify_raw, "wb") as fout:
        fout.truncate(384000 * 4096)
        for i, off in enumerate(offsets):
            fin.seek(off)
            hdr = fin.read(28)
            magic, maj, min_v, f_hdr_sz, c_hdr_sz, blk_sz, tot_blks, tot_chunks, crc = struct.unpack("<I4H4I", hdr)
            cur_blk = 0
            for _ in range(tot_chunks):
                chdr = fin.read(c_hdr_sz)
                c_type, reserved, c_sz, tot_sz = struct.unpack("<2H2I", chdr)
                data_sz = tot_sz - c_hdr_sz
                if c_type == 0xCAC1: # RAW
                    fout.seek(cur_blk * blk_sz)
                    rem_data = data_sz
                    while rem_data > 0:
                        buf = fin.read(min(rem_data, 1048576))
                        fout.write(buf)
                        rem_data -= len(buf)
                elif c_type == 0xCAC2: # FILL
                    fill_val = fin.read(4)
                    pattern = fill_val * (blk_sz // 4)
                    fout.seek(cur_blk * blk_sz)
                    for _ in range(c_sz):
                        fout.write(pattern)
                else: # DONT_CARE
                    if data_sz > 0:
                        fin.seek(data_sz, 1)
                cur_blk += c_sz

    print("[i] Checking filesystem integrity of extracted system image...")
    # Check superblock magic
    with open(verify_raw, "rb") as f:
        f.seek(1024 + 56)
        magic = struct.unpack("<H", f.read(2))[0]
        print(f"  Ext4 Superblock magic: {hex(magic)} (expected: 0xef53)")
        assert magic == 0xef53

    # Run e2fsck in WSL or native Linux
    wsl_img = "/mnt/c/Users/fecam/wanbo-light-os/build/verify_assembled_raw.img"
    e2fsck_cmd = ["e2fsck", "-f", "-n", wsl_img] if os.path.exists("/usr/sbin/e2fsck") else ["wsl", "e2fsck", "-f", "-n", wsl_img]
    res = subprocess.run(e2fsck_cmd, capture_output=True, text=True)
    print("e2fsck verification result:")
    print(res.stdout)
    assert res.returncode == 0

    # Clean up test raw
    os.remove(verify_raw)
    print("[SUCCESS] All firmware integrity and filesystem checks passed 100%!")

if __name__ == "__main__":
    main()
