"""Recover .proto schemas from jadx-decompiled protobuf-java classes.

Each generated *Protos.java embeds its serialized FileDescriptorProto as Java
string literals passed to internalBuildGeneratedFileFrom(); protobuf-java
encodes those bytes as ISO-8859-1 chars.
"""
import re
import sys
from pathlib import Path

from google.protobuf import descriptor_pb2

LABELS = {1: "optional", 2: "required", 3: "repeated"}
TYPES = {v: k[5:].lower() for k, v in descriptor_pb2.FieldDescriptorProto.Type.items()}


def java_unescape(s: str) -> str:
    out, i = [], 0
    while i < len(s):
        c = s[i]
        if c != "\\":
            out.append(c); i += 1; continue
        n = s[i + 1]
        if n == "u":
            out.append(chr(int(s[i + 2:i + 6], 16))); i += 6
        elif n in "01234567":
            m = re.match(r"[0-7]{1,3}", s[i + 1:])
            out.append(chr(int(m.group(0), 8))); i += 1 + len(m.group(0))
        else:
            out.append({"n": "\n", "t": "\t", "r": "\r", "b": "\b", "f": "\f",
                        "'": "'", '"': '"', "\\": "\\"}[n]); i += 2
    return "".join(out)


def extract(java: str) -> bytes | None:
    m = re.search(r"internalBuildGeneratedFileFrom\(new String\[\]\{(.*?)\}, new Descriptors", java, re.S)
    if not m:
        return None
    parts = re.findall(r'"((?:[^"\\]|\\.)*)"', m.group(1), re.S)
    return "".join(java_unescape(p) for p in parts).encode("latin-1")


def render_field(f, indent):
    t = f.type_name.lstrip(".") if f.type_name else TYPES[f.type]
    return f"{indent}{LABELS[f.label]} {t} {f.name} = {f.number};"


def render_msg(msg, indent=""):
    lines = [f"{indent}message {msg.name} {{"]
    inner = indent + "  "
    for e in msg.enum_type:
        lines += render_enum(e, inner)
    for n in msg.nested_type:
        lines += render_msg(n, inner)
    oneofs = {}
    for f in msg.field:
        if f.HasField("oneof_index") and not f.proto3_optional:
            oneofs.setdefault(f.oneof_index, []).append(f)
        else:
            lines.append(render_field(f, inner))
    for idx, fields in oneofs.items():
        lines.append(f"{inner}oneof {msg.oneof_decl[idx].name} {{")
        for f in fields:
            lines.append(render_field(f, inner + "  ").replace("optional ", ""))
        lines.append(f"{inner}}}")
    lines.append(f"{indent}}}")
    return lines


def render_enum(e, indent=""):
    lines = [f"{indent}enum {e.name} {{"]
    lines += [f"{indent}  {v.name} = {v.number};" for v in e.value]
    lines.append(f"{indent}}}")
    return lines


def main(src_root: str, out_dir: str):
    out = Path(out_dir)
    for java_file in sorted(Path(src_root).rglob("*Protos.java")):
        data = extract(java_file.read_text(encoding="utf-8", errors="replace"))
        if not data:
            continue
        fd = descriptor_pb2.FileDescriptorProto()
        fd.ParseFromString(data)
        pkg_dir = out / java_file.parent.parent.name
        pkg_dir.mkdir(parents=True, exist_ok=True)
        lines = [f'syntax = "{fd.syntax or "proto2"}";', f"// source: {fd.name}  package: {fd.package}", ""]
        for e in fd.enum_type:
            lines += render_enum(e) + [""]
        for m in fd.message_type:
            lines += render_msg(m) + [""]
        (pkg_dir / fd.name).write_text("\n".join(lines))
        print(pkg_dir / fd.name, len(fd.message_type), "messages")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
