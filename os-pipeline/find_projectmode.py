import zipfile, struct

with zipfile.ZipFile('build/TyyLauncher.apk') as z:
    dex = z.read('classes3.dex')

string_ids_size, string_ids_off = struct.unpack('<II', dex[56:64])
type_ids_size, type_ids_off = struct.unpack('<II', dex[64:72])
proto_ids_size, proto_ids_off = struct.unpack('<II', dex[72:80])
field_ids_size, field_ids_off = struct.unpack('<II', dex[80:88])
method_ids_size, method_ids_off = struct.unpack('<II', dex[88:96])
class_defs_size, class_defs_off = struct.unpack('<II', dex[96:104])

def get_str(idx):
    """
    Extracts string by index from DEX header.

    @param {int} idx - String table index.
    @returns {str} Extracted string value.
    """
    off = struct.unpack('<I', dex[string_ids_off + idx*4:string_ids_off + (idx+1)*4])[0]
    p = off; l = 0; shift = 0
    while True:
        b = dex[p]; p += 1; l |= (b & 0x7f) << shift
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

pos = 0
def read_uleb():
    """
    Reads unsigned LEB128 variable-length integer from current stream position.

    @returns {int} Decoded integer value.
    """
    global pos
    res = 0; shift = 0
    while True:
        b = dex[pos]; pos += 1; res |= (b & 0x7f) << shift
        if not (b & 0x80): break
        shift += 7
    return res

def main():
    """
    Main entry point for finding ProjectModeActivity methods.

    @returns {None}
    """
    global pos
    for i in range(class_defs_size):
        c_off = class_defs_off + i*32
        class_idx, access_flags, superclass_idx, interfaces_off, source_file_idx, annotations_off, class_data_off, static_values_off = struct.unpack('<8I', dex[c_off:c_off+32])
        c_name = get_type(class_idx)
        if c_name == 'Lcom/chijimlee/tyylauncher/activity/ProjectModeActivity;':
            print('Found ProjectModeActivity')
            pos = class_data_off
            s_f = read_uleb(); i_f = read_uleb(); d_m = read_uleb(); v_m = read_uleb()
            for _ in range(s_f): read_uleb(); read_uleb()
            for _ in range(i_f): read_uleb(); read_uleb()
            m_idx = 0
            for _ in range(d_m):
                m_idx += read_uleb(); a_flags = read_uleb(); code_off = read_uleb()
                class_i, proto_i, name_i = struct.unpack('<HHI', dex[method_ids_off + m_idx*8:method_ids_off + (m_idx+1)*8])
                print('Direct:', get_str(name_i), hex(code_off))
            m_idx = 0
            for _ in range(v_m):
                m_idx += read_uleb(); a_flags = read_uleb(); code_off = read_uleb()
                class_i, proto_i, name_i = struct.unpack('<HHI', dex[method_ids_off + m_idx*8:method_ids_off + (m_idx+1)*8])
                print('Virtual:', get_str(name_i), hex(code_off))

if __name__ == '__main__':
    main()
