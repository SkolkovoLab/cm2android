import struct, sys

def tag_string(name, value):
    nb = name.encode('utf-8'); vb = value.encode('utf-8')
    return b'\x08' + struct.pack('>H', len(nb)) + nb + struct.pack('>H', len(vb)) + vb

# servers.dat = uncompressed NBT: root TAG_Compound{ TAG_List("servers") of TAG_Compound{name, ip} }
name, ip = "CounterMine", "android.cherry.pizza"
server = tag_string("name", name) + tag_string("ip", ip) + b'\x00'  # 0x00 = TAG_End of the compound
# TAG_List "servers": tag id 0x09, name, then list-element-type (0x0A compound), count int, elements
ln = b"servers"
servers_list = b'\x09' + struct.pack('>H', len(ln)) + ln + b'\x0A' + struct.pack('>i', 1) + server
root = b'\x0A' + struct.pack('>H', 0) + servers_list + b'\x00'  # root compound (empty name) + TAG_End
open(sys.argv[1], 'wb').write(root)
print("wrote", sys.argv[1], len(root), "bytes")
