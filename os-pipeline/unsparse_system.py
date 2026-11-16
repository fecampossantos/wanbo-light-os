"""
WanboLightOS - System Partition Unsparse Tool
Extracts and unsparses the 10 system chunks from stock MStar firmware binary
into a clean raw ext4 system image.
"""
import os
import struct
import sys

def unsparse_system_image(stock_bin_path: str, output_raw_path: str):
    """
    Unsparse the 10 MStar sparse system chunks into a raw ext4 filesystem image.
    @param stock_bin_path: Path to stock firmware binary (e.g. T9255B_350R00_CC.stock.bin).
    @param output_raw_path: Destination path for raw ext4 system image.
    """
    offsets = [
        0x1130e000, 0x1770e000, 0x1db0e000, 0x23f0e000, 0x2a30e000,
        0x3070e000, 0x36b0e000, 0x3cf0e000, 0x4330e000, 0x4970e000
    ]
    sizes = [
        0x63ff07c, 0x63ff058, 0x63ff040, 0x63ff058, 0x63ff040,
        0x63ff040, 0x63ff058, 0x63ff040, 0x63ff058, 0x207b058
    ]
    os.makedirs(os.path.dirname(os.path.abspath(output_raw_path)), exist_ok=True)
    print(f"[i] Unsparsing system from {stock_bin_path} -> {output_raw_path}...")
    with open(stock_bin_path, "rb") as fin, open(output_raw_path, "wb") as fout:
        fout.truncate(384000 * 4096)
        for i, (off, sz) in enumerate(zip(offsets, sizes)):
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
                    rem = data_sz
                    while rem > 0:
                        chunk_to_read = min(rem, 1048576)
                        buf = fin.read(chunk_to_read)
                        fout.write(buf)
                        rem -= chunk_to_read
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
            print(f"  Processed sparse chunk {i}/9 ({sz} bytes)")
    raw_size = os.path.getsize(output_raw_path)
    print(f"[SUCCESS] Unsparse complete. Size: {raw_size:,} bytes")

def main():
    """
    CLI entrypoint.
    """
    repo_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    stock_bin = os.path.join(repo_root, "T2MAX-Z9-EN-20220616", "T9255B_350R00_CC.stock.bin")
    output_raw = os.path.join(repo_root, "build", "test_system_raw.img")
    if not os.path.exists(stock_bin):
        stock_bin = os.path.join(repo_root, "T2MAX-Z9-EN-20220616", "T9255B_350R00_CC.bin")
    unsparse_system_image(stock_bin, output_raw)

if __name__ == "__main__":
    main()
