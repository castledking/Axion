#!/usr/bin/env python3
"""Produce a 1.21.10 NeoForge jar from the 1.21.11 build.

MC 1.21.11 upstream renamed net.minecraft.resources.ResourceLocation to
net.minecraft.resources.Identifier. Our single source tree compiles against
the 1.21.11 name; a pure rename means rewriting the internal name in the
compiled constant pools (class refs, descriptors, signature strings, mixin
target strings) yields a 1.21.10-compatible binary.

Also rewrites the neoforge.mods.toml Minecraft range so each jar only loads
on the version it was built for — the two variants are not interchangeable.
"""
import re
import sys
import zipfile

# 1.21.10 -> 1.21.11 internal-name renames (verified by diffing the
# neoforge-21.10.64/-21.11.45 merged class lists against our jar's refs).
RENAMES = [
    (b"net/minecraft/resources/Identifier",
     b"net/minecraft/resources/ResourceLocation"),
    (b"net/minecraft/client/renderer/rendertype/RenderType",
     b"net/minecraft/client/renderer/RenderType"),
]
DOTTED_RENAMES = [
    (b"net.minecraft.resources.Identifier",
     b"net.minecraft.resources.ResourceLocation"),
    (b"net.minecraft.client.renderer.rendertype.RenderType",
     b"net.minecraft.client.renderer.RenderType"),
]

# The convention plugin stamps gradle.properties' shared fabric range into
# neoforge.mods.toml; pin whatever we find to the requested exact version.
MC_RANGE_RE = re.compile(rb'[\[(]1\.21[\d.,]*[\])]')


def strip_mixins(data: bytes, remove: set) -> bytes:
    import json
    cfg = json.loads(data)
    cfg["client"] = [m for m in cfg["client"] if m not in remove]
    return json.dumps(cfg, indent=2).encode()


def rewrite_class(data: bytes) -> bytes:
    """Rewrite the renamed internal name via a constant-pool walk.

    A naive byte replace corrupts the file: CONSTANT_Utf8 entries carry a
    2-byte length prefix, and Identifier -> ResourceLocation changes length.
    We parse the pool, rewrite only Utf8 payloads (class names, descriptors,
    signature strings, mixin targets all live there), and copy the rest
    verbatim — everything outside the pool references entries by index.
    """
    if not any(a in data for a, _ in RENAMES + DOTTED_RENAMES):
        return data
    if data[:4] != b"\xca\xfe\xba\xbe":
        raise ValueError("not a class file")
    pos = 8  # magic(4) + minor(2) + major(2)
    count = int.from_bytes(data[pos : pos + 2], "big")
    pos += 2
    out = bytearray(data[:pos])
    index = 1
    while index < count:
        tag = data[pos]
        pos += 1
        if tag == 1:  # Utf8
            length = int.from_bytes(data[pos : pos + 2], "big")
            pos += 2
            payload = data[pos : pos + length]
            pos += length
            new = payload
            for a, b in RENAMES + DOTTED_RENAMES:
                new = new.replace(a, b)
            out += bytes((1,)) + len(new).to_bytes(2, "big") + new
        elif tag == 15:  # MethodHandle: u1 kind + u2 index
            out += bytes((tag,)) + data[pos : pos + 3]
            pos += 3
        elif tag in (7, 8, 16, 19, 20):  # Class/Str/MethodType/Module/Package
            out += bytes((tag,)) + data[pos : pos + 2]
            pos += 2
        elif tag in (3, 4, 9, 10, 11, 12, 17, 18):
            out += bytes((tag,)) + data[pos : pos + 4]
            pos += 4
        elif tag in (5, 6):  # Long/Double take two pool slots
            out += bytes((tag,)) + data[pos : pos + 8]
            pos += 8
            index += 1
        else:
            raise ValueError(f"unknown constant pool tag {tag}")
        index += 1
    out += data[pos:]
    return bytes(out)


# Mixin classes that import 1.21.9+ types (MouseButtonInfo,
# LevelRenderState). The 1.21.6-1.21.8 jar must not list them — the mixin
# engine would fail to load the class. The legacy variants
# (MouseMixin.onPress, WorldRendererFallbackLegacyMixin) ship everywhere.
PRE_1_21_9_MIXINS = ["MouseModernMixin", "WorldRendererFallbackMixin"]


def mc_range_for(target_mc_version: str) -> str:
    if target_mc_version == "1.21.10":
        # 1.21.9 and 1.21.10 share one Mojmap API surface (both served by
        # NeoForge 21.10.x), so the rewritten jar ranges across both.
        return "[1.21.9,1.21.11)"
    if target_mc_version == "1.21.8":
        # 1.21.6-1.21.8 share one API surface (compiled against 1.21.8,
        # NeoForge 21.8.x).
        return "[1.21.6,1.21.9)"
    return f"[{target_mc_version}]"


def main(src: str, dst: str, target_mc_version: str) -> None:
    replaced_classes = 0
    if src == dst:
        dst = src + ".rewritten"
    with zipfile.ZipFile(src, "r") as zin, zipfile.ZipFile(
        dst, "w", zipfile.ZIP_DEFLATED
    ) as zout:
        for item in zin.infolist():
            data = zin.read(item.filename)
            if item.filename.endswith(".class"):
                if target_mc_version in ("1.21.10", "1.21.8") and any(
                    a in data for a, _ in RENAMES + DOTTED_RENAMES
                ):
                    data = rewrite_class(data)
                    replaced_classes += 1
            elif item.filename.endswith("neoforge.mods.toml"):
                data = MC_RANGE_RE.sub(
                    mc_range_for(target_mc_version).encode(), data
                )
                expected = f'versionRange="{mc_range_for(target_mc_version)}"'.encode()
                if expected not in data:
                    raise SystemExit(
                        f"neoforge.mods.toml Minecraft range was not pinned to {mc_range_for(target_mc_version)}"
                    )
            elif item.filename.endswith("axion.client.mixins.json"):
                if target_mc_version == "1.21.8":
                    data = strip_mixins(data, set(PRE_1_21_9_MIXINS))
            zout.writestr(item, data)
    if dst.endswith(".rewritten"):
        import os
        os.replace(dst, src)
    print(
        f"[mc{target_mc_version}-rewrite] {replaced_classes} classes rewritten -> {src if dst.endswith('.rewritten') else dst}"
    )


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2], sys.argv[3])
