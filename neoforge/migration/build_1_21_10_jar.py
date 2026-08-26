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

SLASH = b"net/minecraft/resources/Identifier"
SLASH_REPL = b"net/minecraft/resources/ResourceLocation"
DOTTED = b"net.minecraft.resources.Identifier"
DOTTED_REPL = b"net.minecraft.resources.ResourceLocation"

# The convention plugin stamps gradle.properties' shared fabric range into
# neoforge.mods.toml; pin whatever we find to the requested exact version.
MC_RANGE_RE = re.compile(rb'(\[1\.21[\d.,\[\]]*\])')


def rewrite_class(data: bytes) -> bytes:
    return data.replace(SLASH, SLASH_REPL).replace(DOTTED, DOTTED_REPL)


def mc_range_for(target_mc_version: str) -> str:
    if target_mc_version == "1.21.10":
        # 1.21.9 and 1.21.10 share one Mojmap API surface (both served by
        # NeoForge 21.10.x), so the rewritten jar ranges across both.
        return "[1.21.9,1.21.11)"
    return f"[{target_mc_version}]"


def main(src: str, dst: str, target_mc_version: str) -> None:
    replaced_classes = 0
    with zipfile.ZipFile(src, "r") as zin, zipfile.ZipFile(
        dst, "w", zipfile.ZIP_DEFLATED
    ) as zout:
        for item in zin.infolist():
            data = zin.read(item.filename)
            if item.filename.endswith(".class"):
                if target_mc_version == "1.21.10" and (SLASH in data or DOTTED in data):
                    data = rewrite_class(data)
                    replaced_classes += 1
            elif item.filename.endswith("neoforge.mods.toml"):
                data = MC_RANGE_RE.sub(
                    mc_range_for(target_mc_version).encode(), data
                )
            zout.writestr(item, data)
    print(
        f"[mc{target_mc_version}-rewrite] {replaced_classes} classes rewritten -> {dst}"
    )


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2], sys.argv[3])
