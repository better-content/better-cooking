"""One bounded strict JSON reader for native observations and their evidence.

Public caps are fixed. Parsing performs a grammar/budget preflight before the
standard decoder allocates the aggregate tree. No admission capability here.
"""
from decimal import Decimal, InvalidOperation
import json
import os
from pathlib import Path
import re
import stat

MAX_RAW_BYTES = 67108864
MAX_DEPTH = 64
MAX_NODES = 2000000
_NUMBER = re.compile(r'-?(?:0|[1-9][0-9]*)(?:\.[0-9]+)?(?:[eE][+-]?[0-9]+)?')
_SURROGATE = re.compile('[\ud800-\udfff]')


def _identity(st):
    return (st.st_dev, st.st_ino, st.st_size, st.st_mtime_ns, st.st_ctime_ns)


def read_bounded(path):
    """Read one canonical owned HOME regular file once, with stable fd identity."""
    try:
        original = os.fspath(path)
        if not isinstance(original, str) or original != str(Path(original)):
            raise ValueError('evidence path spelling must be canonical before Path conversion')
        path = Path(original)
    except TypeError as exc:
        raise ValueError('evidence path must be text/pathlike') from exc
    if not path.is_absolute() or '..' in path.parts or str(path) != os.path.normpath(str(path)):
        raise ValueError('evidence path must be canonical absolute HOME path')
    try:
        path.relative_to('/home/dev')
    except ValueError as exc:
        raise ValueError('evidence outside HOME') from exc
    # Open every directory relative to its already-open parent; O_NOFOLLOW also
    # prevents component-substitution races from redirecting the actual read.
    fd = os.open('/', os.O_RDONLY | os.O_DIRECTORY)
    try:
        for part in path.parts[1:-1]:
            child = os.open(part, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW, dir_fd=fd)
            os.close(fd); fd = child
        filefd = os.open(path.name, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK, dir_fd=fd)
        try:
            before = os.fstat(filefd)
            if not stat.S_ISREG(before.st_mode) or before.st_uid != os.getuid() or before.st_nlink != 1:
                raise ValueError('evidence must be owned regular file with one link')
            if before.st_size > MAX_RAW_BYTES:
                raise ValueError('raw JSON byte cap exceeded')
            chunks, remaining = [], MAX_RAW_BYTES + 1
            while remaining:
                chunk = os.read(filefd, min(1048576, remaining))
                if not chunk: break
                chunks.append(chunk); remaining -= len(chunk)
            raw = b''.join(chunks)
            after = os.fstat(filefd)
            linked = os.stat(path.name, dir_fd=fd, follow_symlinks=False)
            # Check original absolute spelling too: directory ancestry must not
            # have been swapped/moved while this fd was held.
            for parent in (*reversed(path.parents), path):
                if stat.S_ISLNK(os.lstat(parent).st_mode):
                    raise ValueError('evidence symlink appeared during read')
            current = os.stat(path, follow_symlinks=False)
            if (len(raw) > MAX_RAW_BYTES or len(raw) != before.st_size or
                    _identity(before) != _identity(after) or
                    _identity(before) != _identity(linked) or _identity(before) != _identity(current)):
                raise ValueError('evidence changed during bounded read')
            return raw
        finally:
            os.close(filefd)
    except OSError as exc:
        raise ValueError('unsafe or unreadable JSON evidence path') from exc
    finally:
        os.close(fd)


def _preflight(text, max_depth=MAX_DEPTH, max_nodes=MAX_NODES):
    """Private small-bound testing helper; no caller can raise production caps."""
    if not 0 <= max_depth <= MAX_DEPTH or not 1 <= max_nodes <= MAX_NODES:
        raise ValueError('invalid private JSON budget')
    pos, nodes, size = 0, 0, len(text)

    def ws():
        nonlocal pos
        while pos < size and text[pos] in ' \t\r\n': pos += 1

    def string():
        nonlocal pos
        try:
            value, pos = json.decoder.scanstring(text, pos + 1, True)
        except (ValueError, UnicodeError) as exc:
            raise ValueError('invalid JSON string') from exc
        if _SURROGATE.search(value): raise ValueError('unpaired JSON surrogate')
        return value

    def value(depth):
        nonlocal pos, nodes
        if depth > max_depth: raise ValueError('JSON depth cap exceeded')
        nodes += 1
        if nodes > max_nodes: raise ValueError('JSON node cap exceeded')
        ws()
        if pos >= size: raise ValueError('missing JSON value')
        char = text[pos]
        if char == '"':
            string(); return
        if char in '{[':
            pos += 1; ws(); closing = '}' if char == '{' else ']'
            keys = set() if char == '{' else None
            if pos < size and text[pos] == closing: pos += 1; return
            while True:
                ws()
                if keys is not None:
                    if pos >= size or text[pos] != '"': raise ValueError('JSON object key required')
                    key = string()
                    if key in keys: raise ValueError('duplicate decoded JSON key')
                    keys.add(key); ws()
                    if pos >= size or text[pos] != ':': raise ValueError('JSON colon required')
                    pos += 1
                value(depth + 1); ws()
                if pos < size and text[pos] == closing: pos += 1; return
                if pos >= size or text[pos] != ',': raise ValueError('JSON comma/close required')
                pos += 1
        for literal in ('true', 'false', 'null'):
            if text.startswith(literal, pos): pos += len(literal); return
        match = _NUMBER.match(text, pos)
        if not match: raise ValueError('invalid JSON value/number')
        token = match.group(); pos = match.end()
        try:
            if not Decimal(token).is_finite(): raise ValueError('nonfinite JSON number')
        except InvalidOperation as exc:
            raise ValueError('unsupported exact JSON number') from exc

    value(0); ws()
    if pos != size: raise ValueError('trailing JSON data')


def _pairs(pairs):
    result = {}
    for key, value in pairs:
        if key in result: raise ValueError('duplicate decoded JSON key')
        result[key] = value
    return result


def parse_raw(raw):
    """Validate the same bounded bytes; return an UNTRUSTED object, never authority."""
    if type(raw) is not bytes or len(raw) > MAX_RAW_BYTES:
        raise ValueError('raw JSON requires bounded original bytes')
    try:
        text = raw.decode('utf-8', errors='strict')
        _preflight(text)
        result = json.loads(text, object_pairs_hook=_pairs, parse_float=Decimal,
                            parse_int=int, parse_constant=lambda s: (_ for _ in ()).throw(ValueError('nonfinite JSON')))
    except (UnicodeError, RecursionError, InvalidOperation) as exc:
        raise ValueError('invalid bounded JSON') from exc
    if not isinstance(result, dict): raise ValueError('JSON root must be object')
    return result
