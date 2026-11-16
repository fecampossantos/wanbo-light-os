"""
WanboLightOS - Complete Firmware Assembler
Reconstructs the flashable T9255B_350R00_CC.bin binary for USB recovery installation.
Uses ext4 bitmap-aware sparse chunking and MStar CRC2 footer calculation.
"""
import os
import re
import shutil
import struct
import sys
import zlib

def read_ext4_allocation_bitmap(raw_path: str):
    """
    Read the ext4 block allocation bitmap from the filesystem image.
    @param raw_path: Path to ext4 raw image file.
    @return: Tuple of (alloc_map: bytearray, s_blocks_count: int, blk_sz: int).
    """
    with open(raw_path, "rb") as f:
        f.seek(1024)
        sb = f.read(1024)
        s_blocks_count = struct.unpack("<I", sb[4:8])[0]
        log_blk_sz = struct.unpack("<I", sb[24:28])[0]
        blks_per_grp = struct.unpack("<I", sb[32:36])[0]
        desc_sz = struct.unpack("<H", sb[0xfe:0x100])[0] or 32
        blk_sz = 1024 << log_blk_sz
        num_grps = (s_blocks_count + blks_per_grp - 1) // blks_per_grp

        alloc_map = bytearray(s_blocks_count)
        for g in range(num_grps):
            f.seek(blk_sz + g * desc_sz)
            desc = f.read(desc_sz)
            bg_block_bitmap = struct.unpack("<I", desc[:4])[0]
            f.seek(bg_block_bitmap * blk_sz)
            bitmap_data = f.read(blks_per_grp // 8)
            for byte_idx, b in enumerate(bitmap_data):
                for bit in range(8):
                    blk_idx = g * blks_per_grp + byte_idx * 8 + bit
                    if blk_idx < s_blocks_count:
                        alloc_map[blk_idx] = (b >> bit) & 1

    return alloc_map, s_blocks_count, blk_sz

def calculate_chunk_boundaries_ext4(alloc_map: bytearray, tot_blks: int):
    """
    Compute 10 chunk boundaries based on allocated block density.
    Ensures slot 9 (max ~32.48 MB) never overflows by distributing blocks across slots 0..8.
    @param alloc_map: Bytearray where 1 indicates allocated block and 0 indicates free.
    @param tot_blks: Total blocks in partition (384,000).
    @return: List of 10 tuples (start_blk, end_blk).
    """
    total_alloc = sum(alloc_map)
    max_chunk9_blocks = 7500
    if total_alloc > max_chunk9_blocks:
        per_chunk_target = (total_alloc - max_chunk9_blocks + 8) // 9
    else:
        per_chunk_target = (total_alloc + 9) // 10

    per_chunk_target = min(per_chunk_target, 25000)

    boundaries = []
    cur_blk = 0
    start_blk = 0

    for chunk_idx in range(9):
        count = 0
        while cur_blk < tot_blks:
            if alloc_map[cur_blk]:
                count += 1
            cur_blk += 1
            if count >= per_chunk_target:
                break
        boundaries.append((start_blk, cur_blk))
        start_blk = cur_blk

    boundaries.append((start_blk, tot_blks))
    return boundaries

def encode_sparse_chunk_ext4(raw_file, alloc_map: bytearray, start_blk: int, end_blk: int, tot_blks: int, blk_sz: int = 4096) -> bytes:
    """
    Encode an ext4 block range [start_blk, end_blk) into an Android sparse image chunk.
    Allocated blocks are emitted as RAW chunks; unallocated blocks are emitted as DONT_CARE.
    @param raw_file: Opened file handle to raw ext4 image.
    @param alloc_map: Ext4 block allocation map.
    @param start_blk: Starting block index for this chunk.
    @param end_blk: Ending block index for this chunk.
    @param tot_blks: Total blocks in entire partition.
    @param blk_sz: Block size in bytes (4096).
    @return: Raw bytes of the Android sparse image chunk.
    """
    subchunks = []

    # 1. Prefix DONT_CARE if start_blk > 0
    if start_blk > 0:
        subchunks.append((0xCAC3, start_blk, b""))

    # 2. Process blocks in [start_blk, end_blk)
    cur = start_blk
    while cur < end_blk:
        is_alloc = bool(alloc_map[cur])
        run_start = cur
        cur += 1

        while cur < end_blk and bool(alloc_map[cur]) == is_alloc:
            cur += 1
            if is_alloc and (cur - run_start) >= 16384:
                break

        run_len = cur - run_start
        if is_alloc:
            raw_file.seek(run_start * blk_sz)
            data = raw_file.read(run_len * blk_sz)
            subchunks.append((0xCAC1, run_len, data))
        else:
            subchunks.append((0xCAC3, run_len, b""))

    # 3. Suffix DONT_CARE if end_blk < tot_blks
    if end_blk < tot_blks:
        subchunks.append((0xCAC3, tot_blks - end_blk, b""))

    tot_chunks = len(subchunks)
    header = struct.pack("<I4H4I", 0xed26ff3a, 1, 0, 28, 12, blk_sz, tot_blks, tot_chunks, 0)

    out = bytearray(header)
    for c_type, c_sz, data in subchunks:
        tot_sz = 12 + len(data)
        out.extend(struct.pack("<2H2I", c_type, 0, c_sz, tot_sz))
        if data:
            out.extend(data)

    return bytes(out)

def update_header_script(orig_script: str, chunk_sizes: list) -> str:
    """
    Update filepartload sizes in the header script for the 10 system chunks.
    @param orig_script: Original ~header_script text.
    @param chunk_sizes: List of 10 integers representing byte size of each chunk.
    @return: Modified script string.
    """
    lines = orig_script.splitlines()
    new_lines = []
    chunk_idx = 0
    in_system_section = False

    for line in lines:
        if "# File Partition: system" in line:
            in_system_section = True
            new_lines.append(line)
            continue
        if in_system_section and "# File Partition:" in line:
            in_system_section = False

        if in_system_section and line.startswith("filepartload 0x50000000 $(UpgradeImage)"):
            parts = line.split()
            offset = parts[3]
            new_size_hex = f"0x{chunk_sizes[chunk_idx]:x}"
            new_line = f"filepartload 0x50000000 $(UpgradeImage) {offset} {new_size_hex}"
            new_lines.append(new_line)
            chunk_idx += 1
        else:
            new_lines.append(line)

    return "\n".join(new_lines) + "\n"

def assemble_firmware(stock_bin_path: str, raw_img_path: str, output_bin_path: str, tvconfig_path: str = None):
    """
    Assemble the complete flashable MStar firmware binary.
    @param stock_bin_path: Path to original stock T9255B_350R00_CC.bin.
    @param raw_img_path: Path to modified test_system_raw.img.
    @param output_bin_path: Path to destination T9255B_350R00_CC.bin.
    @param tvconfig_path: Optional path to modified tvconfig.img filesystem.
    """
    print(f"[i] Copying stock base firmware from {stock_bin_path} to {output_bin_path}...")
    shutil.copyfile(stock_bin_path, output_bin_path)

    if tvconfig_path and os.path.exists(tvconfig_path):
        print(f"[i] Injecting patched tvconfig partition from {tvconfig_path}...")
        tvconfig_off = 0x804000
        tvconfig_size = 0x2000000
        with open(tvconfig_path, "rb") as f_tv, open(output_bin_path, "r+b") as f_out:
            data = f_tv.read()
            if len(data) != tvconfig_size:
                raise ValueError(f"tvconfig image size ({len(data)}) must be exactly {tvconfig_size} bytes!")
            f_out.seek(tvconfig_off)
            f_out.write(data)
        print(f"  tvconfig injected at offset 0x{tvconfig_off:x} ({len(data):,} bytes).")

    print("[i] Reading ext4 block allocation bitmap...")
    alloc_map, tot_blks, blk_sz = read_ext4_allocation_bitmap(raw_img_path)
    total_alloc = sum(alloc_map)
    print(f"  Ext4 total blocks: {tot_blks}, Allocated: {total_alloc} ({total_alloc * blk_sz / 1024 / 1024:.2f} MB)")

    print("[i] Computing optimal chunk boundaries for 10 slots...")
    boundaries = calculate_chunk_boundaries_ext4(alloc_map, tot_blks)

    offsets = [
        0x1130e000, 0x1770e000, 0x1db0e000, 0x23f0e000, 0x2a30e000,
        0x3070e000, 0x36b0e000, 0x3cf0e000, 0x4330e000, 0x4970e000
    ]
    slot_size = 0x6400000 # 100 MB
    end_of_slot9 = 0x4b78a000

    chunk_sizes = []
    print("[i] Encoding and injecting 10 system sparse chunks into binary...")
    with open(raw_img_path, "rb") as f_raw, open(output_bin_path, "r+b") as f_out:
        for i, ((s_blk, e_blk), off) in enumerate(zip(boundaries, offsets)):
            print(f"  Encoding Chunk {i}: blocks [{s_blk}, {e_blk}) at offset 0x{off:x}...")
            chunk_data = encode_sparse_chunk_ext4(f_raw, alloc_map, s_blk, e_blk, tot_blks, blk_sz)
            c_len = len(chunk_data)
            chunk_sizes.append(c_len)

            if i < 9:
                avail_slot = slot_size
            else:
                avail_slot = end_of_slot9 - off

            if c_len > avail_slot:
                raise ValueError(f"Chunk {i} size {c_len} ({c_len/1024/1024:.2f} MB) exceeds available slot {avail_slot} ({avail_slot/1024/1024:.2f} MB)!")

            f_out.seek(off)
            f_out.write(chunk_data)

            pad_len = avail_slot - c_len
            if pad_len > 0:
                rem_pad = pad_len
                pad_buf = b"\xff" * min(rem_pad, 1048576)
                while rem_pad > 0:
                    write_sz = min(rem_pad, len(pad_buf))
                    f_out.write(pad_buf[:write_sz])
                    rem_pad -= write_sz

            print(f"    Chunk {i} written: {c_len:,} bytes ({c_len/1024/1024:.2f} MB), padded {pad_len:,} bytes.")

    # 2. Update Header Script
    print("[i] Updating header script with new chunk sizes...")
    with open(stock_bin_path, "rb") as f_orig:
        orig_hdr = f_orig.read(16384)
    ff_idx = orig_hdr.find(b"\xff")
    orig_script = orig_hdr[:ff_idx].decode("iso-8859-1")

    new_script = update_header_script(orig_script, chunk_sizes)
    new_script_bytes = new_script.encode("iso-8859-1")
    if len(new_script_bytes) > 16384:
        raise ValueError(f"Header script size ({len(new_script_bytes)}) exceeds 16KB limit!")

    new_hdr = new_script_bytes + (b"\xff" * (16384 - len(new_script_bytes)))
    header_crc = zlib.crc32(new_hdr) & 0xffffffff
    print(f"  New Header CRC: 0x{header_crc:08x}")

    # Write new header at offset 0
    with open(output_bin_path, "r+b") as f_out:
        f_out.seek(0)
        f_out.write(new_hdr)

        # 3. Write Footer Step 1: MAGIC + HEADER_CRC at total_sz - 32
        total_sz = os.path.getsize(output_bin_path)
        f_out.seek(total_sz - 32)
        f_out.write(b"12345678")
        f_out.write(struct.pack("<I", header_crc))

        # 4. Calculate whole-file CRC2 (everything up to total_sz - 20)
        print("[i] Calculating XGIMI/MStar whole-firmware CRC2...")
        f_out.seek(0)
        crc = 0
        rem = total_sz - 20
        while rem > 0:
            chunk = f_out.read(min(rem, 8 * 1024 * 1024))
            crc = zlib.crc32(chunk, crc)
            rem -= len(chunk)
        crc2 = crc & 0xffffffff
        print(f"  New Firmware CRC2: 0x{crc2:08x}")

        # 5. Write Footer Step 2: CRC2 + FIRST16 at total_sz - 20
        f_out.seek(total_sz - 20)
        f_out.write(struct.pack("<I", crc2))
        f_out.write(new_hdr[:16])

    final_size = os.path.getsize(output_bin_path)
    print(f"[SUCCESS] Firmware binary assembled successfully: {output_bin_path}")
    print(f"  Total size: {final_size:,} bytes (matches factory size: {final_size == 1601388576})")

def main():
    """
    Main entry point for firmware assembly script.

    @returns {None}
    """
    repo_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    stock_bin = os.path.join(repo_root, "T2MAX-Z9-EN-20220616", "T9255B_350R00_CC.stock.bin")
    if not os.path.exists(stock_bin):
        stock_bin = os.path.join(repo_root, "T2MAX-Z9-EN-20220616", "T9255B_350R00_CC.bin")
    raw_img = os.path.join(repo_root, "build", "test_system_raw.img")
    output_bin = os.path.join(repo_root, "build", "WanboLightOS_v1.0.bin")
    tvconfig_img = None

    assemble_firmware(stock_bin, raw_img, output_bin, tvconfig_img)

if __name__ == "__main__":
    main()
