# Padel score (Wear OS)

Scoreteller voor padel op een Android-horloge (Wear OS 3 of nieuwer).

## Gebruik
- Startscherm: kies gouden punt aan/uit, 1 set of best of 3, en wie begint met opslaan.
- Tik op de linkerhelft (Wij, blauw) of rechterhelft (Zij, rood) voor een punt.
- Gele stip = dit team serveert. Bij 6-6 volgt een tiebreak tot 7 (2 verschil).
- ↶ maakt het laatste punt ongedaan. Terugknop vraagt of je de partij wilt stoppen.
- De stand wordt opgeslagen, dus als de app wordt afgesloten gaat er niets verloren.

## APK downloaden
Elke push bouwt via GitHub Actions een APK en zet die als release neer.
Nieuwste versie: https://github.com/micha35fey/apphorloge/releases/latest (bestand `padelscore.apk`).

## Updaten
De app kijkt bij het opstarten op GitHub of er een nieuwere release is. Zo ja, dan staat er
bovenaan het startscherm een knop "Update naar 1.x". De eerste keer vraagt het horloge of de
app mag installeren; daarna bevestig je alleen nog de installatie.
Dit werkt alleen als de repo openbaar is.

Alle builds worden ondertekend met `app/padelscore.keystore`, zodat een nieuwe versie over de
oude heen installeert. Niet weggooien: een andere sleutel betekent eerst de app verwijderen.

## Installeren op het horloge
1. Horloge: Instellingen > Systeem > Info > tik 7x op Build-nummer.
2. Ontwikkelaarsopties: zet ADB-foutopsporing en Foutopsporing via wifi aan.
3. Pc: `adb connect <ip-van-horloge>:<poort>` en daarna `adb install app-debug.apk`.
