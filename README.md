# 🎬 KinoCast (16:9 Cinema Streamer)

Android telefondan Windows kompyuteriga filmlarni to'g'ridan-to'g'ri (Wi-Fi tochka orqali) uzatish dasturi.

---

## 🌟 Asosiy Xususiyatlari

1. **Faqat 16:9 Kino Rejimida Uzatish:**
   - Telefon vertikal turganda shaxsiy ekran, chatlar yoki bildirishnomalar kompyuterda **KO'RINMAYDI**.
   - Faqat telefonni gorizontal (16:9) qilib kino qo'yilgandagina kompyuterda ko'rinadi.
2. **Bildirishnoma va Shovqinsiz Toza Ovoz:**
   - Android 10+ `AudioPlaybackCapture` texnologiyasi orqali **faqat kino/media ovozi** kompyuterga uzatiladi.
   - Telegram, WhatsApp, qo'ng'iroq yoki tizim bildirishnomalari ovozi kompyuterga o'tmaydi.
3. **0 MB Internet Sarfi:**
   - Telefon Wi-Fi tochka (Hotspot) yoqadi va kompyuter unga ulanadi. Barcha ma'lumot lokal tarmoq orqali o'tadi — mobil internet trafigi ketmaydi.
4. **Kompyuterni Og'irlashtirmaydigan Qurish:**
   - Hech qanday og'ir SDK yoki gigabaytli dasturlar kompyuteringizga o'rnatilmaydi. GitHub Actions barcha fayllarni bulutda yig'ib beradi.

---

## 🚀 Tayyor APK va EXE Fayllarni Olish (GitHub Actions orqali)

Ushbu loyihani o'zingizning GitHub hisobingizga joylab, 2 daqiqada tayyor fayllarni olishingiz mumkin:

1. **GitHub da yangi repozitoriy yarating** (masalan, `kino-cast`).
2. Ushbu papkadagi barcha fayllarni repozitoriyingizga yuklang (push qiling).
3. Repozitoriyingizning **Actions** bo'limiga kiring:
   - `Build KinoCast (APK & EXE)` avtomatik ishga tushadi.
   - 2-3 daqiqadan so'ng **Releases** bo'limida:
     - 📱 `FilmCaster.apk` (Android telefon uchun)
     - 💻 `FilmReceiver.exe` (Windows kompyuter uchun)
     tayyor holatda yuklab olishga taqdim etiladi!

---

## 📱 Kompyuter va Telefonni Ishlatish Bo'yicha Qo'llanma

1. **Telefonda:**
   - Wi-Fi tochkani (Mobile Hotspot) yoqing.
   - `FilmCaster.apk` ilovasini oching.
   - **"Uzatishni Boshlash"** tugmasini bosing va ekranni yozib olishga ruxsat bering.
2. **Kompyuterda:**
   - Noutbuk/kompyuteringiz Wi-Fi qismidan telefoningiz tochkasiga ulaning.
   - **`FilmReceiver.exe`** ni ishga tushiring (yoki brauzerda `http://192.168.43.1:8080` ni oching).
   - `F11` tugmasini bosib to'liq ekranga (cinema rejimiga) o'tkazing.
3. **Kino Ko'rish:**
   - Telefonda kinoni oching va telefonni gorizontal holatga buring.
   - Kino to'liq 16:9 formatda kompyuterda yangraydi va aks etadi!
