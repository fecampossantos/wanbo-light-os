
"""
Disassembles onReceive bytecode from TyyLauncher classes3.dex.
"""
import struct
import zipfile

def main():
    """
    Main entry point for onReceive bytecode disassembly.

    @returns {None}
    """
    with zipfile.ZipFile('build/TyyLauncher.apk') as z:
        dex = z.read('classes3.dex')

    code_off = 0x145e0c
    insns_sz = struct.unpack('<I', dex[code_off+12:code_off+16])[0]
    insns = dex[code_off+16:code_off+16+insns_sz*2]

    # Print instructions from offset 0x48 to 0x80
    for pc in range(0x48, 0x80, 2):
        op = insns[pc]
        arg = struct.unpack('<H', insns[pc:pc+2])[0]
        print(hex(pc), hex(op), hex(arg))

if __name__ == '__main__':
    main()
