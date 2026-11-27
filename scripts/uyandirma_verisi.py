#!/usr/bin/env python3
"""
"Hey Egea" algılayıcısı için değerlendirme verisi üretir: espeak-ng (Türkçe sesler) + araç içi uğultu,
16 kHz mono WAV. Klasör yapısı: <çıktı>/<konuşmacı>/{ogret,pozitif,negatif}/*.wav
(ogret: dosya adı sırasıyla 2 × "Hey Egea", 2 × "Hey Ege" — uygulamadaki öğretme sırası).

Gerekenler: espeak-ng, ffmpeg, python3-numpy.

  # Geliştirici değerlendirmesi (büyük set, 16-bit PCM):
  python3 scripts/uyandirma_verisi.py --cikti /tmp/sesler20 --snr 20
  EGEA_SES_VERISI=/tmp/sesler20 ./gradlew :core:test --tests '*UyandirmaDegerlendirme*'   # rapor.txt

  # Repodaki küçük regresyon seti (core/src/test/resources/uyandirma):
  python3 scripts/uyandirma_verisi.py --cikti core/src/test/resources/uyandirma --regresyon

Not: Sentetik sesler gerçek bir insandan daha az değişkendir; mutlak oranlar gerçek kullanımda farklı olur,
ama yöntemleri karşılaştırmak için güvenilirdir.
"""
import argparse
import os
import struct
import subprocess
import tempfile

import numpy as np

KONUSMACILAR = ["male1", "male3", "male5", "female1", "female3", "klatt", "Andy", "Annie"]
OGRET = [("Hey Egea", 165, 50), ("Hey Egea", 150, 55), ("Hey Ege", 165, 50), ("Hey Ege", 150, 45)]
POZITIF = [
    ("Hey Egea", 140, 40), ("Hey Egea", 185, 60), ("Hey Egea", 200, 50), ("Hey Egea", 170, 65),
    ("Hey Egea!", 160, 50), ("Hey Ege", 140, 60), ("Hey Ege", 185, 40), ("Hey Ege", 200, 55),
    ("Hey Ege!", 175, 50),
]
NEGATIF = [
    "Hey Google", "Hey Siri", "Egea", "Ege", "Hey", "Eve git", "Kral FM aç", "Radyoyu aç", "Merhaba",
    "Nasılsın", "Egemen", "Hey gidi", "Ege bölgesi", "Hey Elif", "Hey abi", "Hey Emre", "Heykel", "Ece",
    "Egeli", "Eğer", "Hey Erdem", "Hey Ayşe", "Ne haber", "Tamam", "Sonraki şarkı", "Sesi aç",
    "Teşekkürler", "Hadi gel", "Evet", "Hayır", "Hey Kemal", "Hey Engin", "Bugün hava güzel",
]
# Regresyon setinde yalnız en çok karışan (zor) negatifler.
ZOR_NEGATIF = ["Egea", "Ege", "Ece", "Hey Emre", "Hey Kemal", "Hey Erdem", "Egemen", "Hey Google", "Merhaba", "Kral FM aç"]


def espeak(metin, ses, hiz, perde):
    with tempfile.TemporaryDirectory() as d:
        ham = os.path.join(d, "a.wav")
        cevrik = os.path.join(d, "b.raw")
        subprocess.run(["espeak-ng", "-v", f"tr+{ses}", "-s", str(hiz), "-p", str(perde), "-w", ham, metin],
                       check=True, capture_output=True)
        subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", ham, "-ar", "16000", "-ac", "1",
                        "-f", "s16le", cevrik], check=True)
        with open(cevrik, "rb") as f:
            return np.frombuffer(f.read(), dtype=np.int16).astype(np.float32) / 32768.0


def arac_gurultusu(rng, n):
    """Kahverengi (1/f^2) ağırlıklı araç içi uğultu + az beyaz gürültü."""
    beyaz = rng.standard_normal(n).astype(np.float32)
    kahve = np.cumsum(beyaz)
    kahve -= np.convolve(kahve, np.ones(801) / 801, mode="same")
    kahve /= (np.std(kahve) + 1e-9)
    return 0.85 * kahve + 0.15 * rng.standard_normal(n).astype(np.float32)


def karistir(rng, konusma, snr_db, kazanc, on_sn, arka_sn):
    s = konusma / (np.max(np.abs(konusma)) + 1e-9) * 0.5 * kazanc
    on = int(16000 * rng.uniform(*on_sn))
    arka = int(16000 * rng.uniform(*arka_sn))
    toplam = np.concatenate([np.zeros(on, np.float32), s, np.zeros(arka, np.float32)])
    etkin = s[np.abs(s) > 0.02 * kazanc]
    p_konusma = float(np.mean(etkin ** 2)) if etkin.size else 1e-6
    g = arac_gurultusu(rng, toplam.size)
    g *= np.sqrt(p_konusma / (float(np.mean(g ** 2)) * 10 ** (snr_db / 10.0)))
    return np.clip(toplam + g, -1, 1)


def mulaw(x):
    """G.711 mu-law kodlama (8 bit)."""
    pcm = np.clip((x * 32767).astype(np.int32), -32635, 32635)
    isaret = np.where(pcm < 0, 0x80, 0)
    pcm = np.abs(pcm) + 0x84
    us = np.floor(np.log2(pcm)).astype(np.int32) - 7
    us = np.clip(us, 0, 7)
    mantis = (pcm >> (us + 3)) & 0x0F
    return (~(isaret | (us << 4) | mantis) & 0xFF).astype(np.uint8)


def yaz(yol, x, mu):
    os.makedirs(os.path.dirname(yol) or ".", exist_ok=True)
    if mu:
        veri = mulaw(x).tobytes()
        fmt = struct.pack("<HHIIHH", 7, 1, 16000, 16000, 1, 8)
    else:
        veri = (x * 32767).astype(np.int16).tobytes()
        fmt = struct.pack("<HHIIHH", 1, 1, 16000, 32000, 2, 16)
    with open(yol, "wb") as f:
        f.write(b"RIFF" + struct.pack("<I", 4 + 8 + len(fmt) + 8 + len(veri)) + b"WAVE")
        f.write(b"fmt " + struct.pack("<I", len(fmt)) + fmt)
        f.write(b"data" + struct.pack("<I", len(veri)) + veri)


def ad(metin):
    tablo = str.maketrans("çğıöşüÇĞİÖŞÜ", "cgiosuCGIOSU")
    return "".join(ch if ch.isalnum() else "_" for ch in metin.translate(tablo)).strip("_").lower()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--cikti", required=True)
    ap.add_argument("--snr", type=float, default=20.0)
    ap.add_argument("--konusmacilar", default=",".join(KONUSMACILAR))
    ap.add_argument("--regresyon", action="store_true", help="küçük set: 2 konuşmacı, kısa dolgu, mu-law, zor negatifler")
    ap.add_argument("--tohum", type=int, default=1234)
    a = ap.parse_args()
    rng = np.random.default_rng(a.tohum)
    konusmacilar = ["male1", "female1"] if a.regresyon else a.konusmacilar.split(",")
    negatifler = ZOR_NEGATIF if a.regresyon else NEGATIF
    on_sn, arka_sn = ((0.3, 0.35), (0.4, 0.45)) if a.regresyon else ((0.5, 0.8), (0.7, 1.0))
    for k in konusmacilar:
        for i, (m, s, p) in enumerate(OGRET):
            x = karistir(rng, espeak(m, k, s, p), 30.0, 1.0, on_sn, arka_sn)
            yaz(f"{a.cikti}/{k}/ogret/{i}_{ad(m)}.wav", x, a.regresyon)
        for i, (m, s, p) in enumerate(POZITIF):
            x = karistir(rng, espeak(m, k, s, p), a.snr, rng.uniform(0.5, 1.2), on_sn, arka_sn)
            yaz(f"{a.cikti}/{k}/pozitif/{i}_{ad(m)}_{s}_{p}.wav", x, a.regresyon)
        for i, m in enumerate(negatifler):
            s = int(rng.choice([150, 165, 180]))
            p = int(rng.choice([40, 50, 60]))
            x = karistir(rng, espeak(m, k, s, p), a.snr, rng.uniform(0.5, 1.2), on_sn, arka_sn)
            yaz(f"{a.cikti}/{k}/negatif/{i}_{ad(m)}.wav", x, a.regresyon)
        print("tamam:", k, flush=True)


if __name__ == "__main__":
    main()
