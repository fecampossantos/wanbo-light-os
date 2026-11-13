import zipfile, struct

with zipfile.ZipFile('build/TyyLauncher.apk') as z:
    dex = z.read('classes3.dex')

string_ids_size, string_ids_off = struct.unpack('<II', dex[56:64])
type_ids_size, type_ids_off = struct.unpack('<II', dex[64:72])
proto_ids_size, proto_ids_off = struct.unpack('<II', dex[72:80])
field_ids_size, field_ids_off = struct.unpack('<II', dex[80:88])
method_ids_size, method_ids_off = struct.unpack('<II', dex[88:96])

def get_str(idx):
    """
    Extracts string by index from DEX header.

    @param {int} idx - String table index.
    @returns {str} Extracted string value.
    """
    off = struct.unpack('<I', dex[string_ids_off + idx*4:string_ids_off + (idx+1)*4])[0]
    p = off
    l = 0
    shift = 0
    while True:
        b = dex[p]
        p += 1
        l |= (b & 0x7f) << shift
        if not (b & 0x80): break
        shift += 7
    return dex[p:p+l].decode('utf-8', errors='replace')

def get_type(idx):
    """
    Extracts type descriptor string by index from DEX type table.

    @param {int} idx - Type table index.
    @returns {str} Type descriptor string.
    """
    desc_idx = struct.unpack('<I', dex[type_ids_off + idx*4:type_ids_off + (idx+1)*4])[0]
    return get_str(desc_idx)

def get_method(idx):
    """
    Extracts method signature representation by method table index.

    @param {int} idx - Method table index.
    @returns {str} Formatted method signature.
    """
    class_i, proto_i, name_i = struct.unpack('<HHI', dex[method_ids_off + idx*8:method_ids_off + (idx+1)*8])
    return get_type(class_i) + '->' + get_str(name_i)

def main():
    """
    Main entry point for setMirrorMode disassembly.

    @returns {None}
    """
    code_off = 0x194304
    reg_sz, ins_sz, outs_sz, tries_sz, debug_off, insns_sz = struct.unpack('<4HII', dex[code_off:code_off+16])
    insns = dex[code_off+16:code_off+16+insns_sz*2]

    pc = 0
    while pc < len(insns):
        op = insns[pc]
        if op == 0x1a:
            str_idx = struct.unpack('<H', insns[pc+2:pc+4])[0]
            print(f'  {hex(pc)}: const-string "{get_str(str_idx)}"')
            pc += 4
        elif op in (0x6e, 0x71, 0x72):
            meth_idx = struct.unpack('<H', insns[pc+2:pc+4])[0]
            print(f'  {hex(pc)}: invoke {get_method(meth_idx)}')
            pc += 6
        else:
            pc += 2

if __name__ == '__main__':
    main()
