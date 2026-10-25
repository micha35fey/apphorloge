# Padel score (Wear OS)

Scoreteller voor padel op een Android-horloge (Wear OS 3 of nieuwer).

## Gebruik
- Startscherm: kies gouden punt aan/uit, 1 set of best of 3, en wie begint met opslaan.
- Tik op de linkerhelft (Wij, blauw) of rechterhelft (Zij, rood) voor een punt.
- Gele stip = dit team serveert. Bij 6-6 volgt een tiebreak tot 7 (2 verschil).
- ↶ maakt het laatste punt ongedaan. Terugknop vraagt of je de partij wilt stoppen.
- De stand wordt opgeslagen, dus als de app wordt afgesloten gaat er niets verloren.

## APK downloaden
Elke push bouwt via GitHub Actions een APK. Ga naar tab **Actions**, open de laatste run
en download het artifact `padelscore-apk`.

## Installeren op het horloge
1. Horloge: Instellingen > Systeem > Info > tik 7x op Build-nummer.
2. Ontwikkelaarsopties: zet ADB-foutopsporing en Foutopsporing via wifi aan.
3. Pc: `adb connect <ip-van-horloge>:<poort>` en daarna `adb install app-debug.apk`.
