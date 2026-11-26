#!/usr/bin/env python3
"""uiautomator dump XML'ini okur.
  ui_metin.py dosya.xml            → ekrandaki metinleri sırayla yazar
  ui_metin.py dosya.xml "Metin"    → o metni içeren ilk düğmenin merkez koordinatını "x y" yazar
"""
import re
import sys
import xml.etree.ElementTree as ET

def dugumler(kok):
    for d in kok.iter("node"):
        yield d

def main():
    yol = sys.argv[1]
    try:
        kok = ET.parse(yol).getroot()
    except Exception as e:
        print(f"(ui dump okunamadı: {e})")
        return
    if len(sys.argv) > 2:
        aranan = sys.argv[2]
        for d in dugumler(kok):
            if aranan in (d.get("text") or ""):
                m = re.findall(r"\d+", d.get("bounds") or "")
                if len(m) == 4:
                    x1, y1, x2, y2 = map(int, m)
                    print((x1 + x2) // 2, (y1 + y2) // 2)
                    return
        print("")
        return
    paket_goruldu = set()
    for d in dugumler(kok):
        paket_goruldu.add(d.get("package") or "")
        t = (d.get("text") or "").strip()
        c = (d.get("content-desc") or "").strip()
        if t:
            print(t)
        elif c:
            print(f"[{c}]")
    print(f"(paketler: {', '.join(sorted(p for p in paket_goruldu if p))})")

if __name__ == "__main__":
    main()
