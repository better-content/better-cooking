"""Small lossless NBT codec for exact pinned structure-palette resource reviews.

Primitive numeric/array payloads and modified-UTF8 strings remain raw bytes.
No NBT library, game execution or unrelated structure editing is involved.
"""
from dataclasses import dataclass
import gzip
import io
import struct


@dataclass
class Tag:
    kind: int
    value: object


def take(stream, count):
    if count < 0: raise ValueError('negative NBT length')
    data = stream.read(count)
    if len(data) != count: raise ValueError('truncated NBT')
    return data


def string(stream):
    return take(stream, struct.unpack('>H', take(stream, 2))[0])


def payload(stream, kind, depth=0):
    if depth > 512: raise ValueError('NBT nesting exceeds bound')
    widths = {1: 1, 2: 2, 3: 4, 4: 8, 5: 4, 6: 8}
    if kind in widths: return Tag(kind, take(stream, widths[kind]))
    if kind == 8: return Tag(kind, string(stream))
    if kind in (7, 11, 12):
        count = take(stream, 4); length = struct.unpack('>i', count)[0]
        return Tag(kind, count + take(stream, length * {7: 1, 11: 4, 12: 8}[kind]))
    if kind == 9:
        subtype = take(stream, 1)[0]; length = struct.unpack('>i', take(stream, 4))[0]
        if length < 0 or length > 1000000 or subtype == 0 and length: raise ValueError('invalid NBT list')
        return Tag(kind, (subtype, [payload(stream, subtype, depth + 1) for _ in range(length)]))
    if kind == 10:
        entries = []
        while True:
            subtype = take(stream, 1)[0]
            if subtype == 0: break
            name = string(stream); entries.append((name, payload(stream, subtype, depth + 1)))
        return Tag(kind, entries)
    raise ValueError('unknown NBT type: ' + str(kind))


def utf(raw): return struct.pack('>H', len(raw)) + raw


def encode_payload(tag):
    if tag.kind == 8: return utf(tag.value)
    if tag.kind == 9:
        subtype, values = tag.value
        return bytes([subtype]) + struct.pack('>i', len(values)) + b''.join(encode_payload(value) for value in values)
    if tag.kind == 10:
        return b''.join(bytes([value.kind]) + utf(name) + encode_payload(value) for name, value in tag.value) + b'\x00'
    return tag.value


def decode(raw):
    data = gzip.decompress(raw) if raw[:2] == b'\x1f\x8b' else raw
    stream = io.BytesIO(data); kind = take(stream, 1)[0]; name = string(stream)
    root = payload(stream, kind)
    if stream.read(): raise ValueError('trailing NBT data')
    if bytes([kind]) + utf(name) + encode_payload(root) != data: raise ValueError('NBT codec did not preserve original payload')
    return name, root


def encode(name, root): return gzip.compress(bytes([root.kind]) + utf(name) + encode_payload(root), mtime=0)


def get(compound, name):
    if compound.kind != 10: raise ValueError('expected NBT compound')
    return next((tag for key, tag in compound.value if key == name.encode()), None)


def canonical_finite_forage(raw, allow_no_changes=False):
    name, root = decode(raw); palette = get(root, 'palette'); palettes = get(root, 'palettes')
    arrays = [palette] if palette else palettes.value[1] if palettes else []
    changes = []
    for ordinal, entries in enumerate(arrays):
        if entries.kind != 9 or entries.value[0] != 10: raise ValueError('invalid structure palette')
        for index, entry in enumerate(entries.value[1]):
            block = get(entry, 'Name')
            if not block or block.kind != 8 or block.value != b'kaleidoscope_cookery:tomato_crop': continue
            props = get(entry, 'Properties'); removed = {}
            if props:
                if props.kind != 10 or any(key != b'age' or value.kind != 8 for key, value in props.value):
                    raise ValueError('new tomato state property requires explicit review')
                removed = {key.decode(): value.value.decode() for key, value in props.value}
                entry.value = [(key, value) for key, value in entry.value if key != b'Properties']
            block.value = b'farmersdelight:wild_tomatoes'
            changes.append({'palette': ordinal, 'entry': index, 'from': 'kaleidoscope_cookery:tomato_crop',
                            'to': 'farmersdelight:wild_tomatoes', 'removed_incompatible_properties': removed})
    if not changes:
        if not allow_no_changes: raise ValueError('expected exact tomato palette entry missing')
        return raw, changes
    return encode(name, root), changes
