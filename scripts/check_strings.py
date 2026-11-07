#!/usr/bin/env python3
"""Comprueba que ca/es tienen las mismas cadenas que el inglés (values/) y los mismos marcadores %d/%s."""
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

RES = Path(__file__).resolve().parent.parent / "app/src/main/res"
PH = re.compile(r"%(\d+\$)?[ds]")


def load(folder):
    root = ET.parse(RES / folder / "strings.xml").getroot()
    out = {
        s.get("name"): "".join(s.itertext())
        for s in root.findall("string")
        if s.get("translatable") != "false"
    }
    # Plurales: se comparan por su forma «other».
    for p in root.findall("plurals"):
        other = next((i for i in p.findall("item") if i.get("quantity") == "other"), None)
        out["plurals:" + p.get("name")] = "".join(other.itertext()) if other is not None else ""
    return out


base = load("values")
ok = True
for loc in ("values-es", "values-ca"):
    other = load(loc)
    missing = sorted(set(base) - set(other))
    extra = sorted(set(other) - set(base))
    bad = sorted(k for k in base.keys() & other.keys() if PH.findall(base[k]) != PH.findall(other[k]))
    for label, items in (("faltan", missing), ("sobran", extra), ("marcadores distintos", bad)):
        if items:
            ok = False
            print(f"{loc}: {label}: {', '.join(items)}")
print("Traducciones OK" if ok else "Traducciones incompletas")
sys.exit(0 if ok else 1)
