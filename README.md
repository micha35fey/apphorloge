# Winner Padel (Wear OS)

Scoreteller voor padel op een Android-horloge (Wear OS 3 of nieuwer).

## Gebruik
- Startscherm: kies gouden punt aan/uit, best of 3, 1 set of alleen games (totaal loopt door, geen sets), en wie begint met opslaan.
- Tik op de linkerhelft (Wij, blauw) of rechterhelft (Zij, rood) voor een punt.
- Gele stip = dit team serveert. Bij 6-6 volgt een tiebreak tot 7 (2 verschil).
- ↶ maakt het laatste punt ongedaan. Terugknop vraagt of je de partij wilt stoppen.
- Scherm aan (startscherm): houdt het scherm aan tijdens de partij. Uit spaart accu.
- De stand wordt opgeslagen, dus als de app wordt afgesloten gaat er niets verloren.

## Telefoon-app
Op je telefoon zie je de stand live, via de gewone koppeling tussen horloge en telefoon.
Zolang er een partij bezig is staat er ook een melding met de stand, en blijft het scherm
van de telefoon aan als de app open staat.
Download: `padelscore-telefoon.apk` bij de nieuwste release met "telefoon" in de naam op
https://github.com/micha35fey/apphorloge/releases

## Projectindeling
- `app`: horloge-app (Wear OS)
- `mobile`: telefoon-app
- `core`: gedeelde code: scoretelling, updater, synchronisatie en icoon

## APK downloaden
Elke push bouwt via GitHub Actions een APK en zet die als release neer.
Nieuwste versie: https://github.com/micha35fey/apphorloge/releases/latest (bestand `padelscore.apk`).

## Updaten
De app kijkt bij het opstarten op GitHub of er een nieuwere release is. Zo ja, dan staat er
bovenaan het startscherm een knop "Update naar 1.x". De eerste keer vraagt het horloge of de
app mag installeren; daarna bevestig je alleen nog de installatie.
Dit werkt alleen als de repo openbaar is.

Vanaf de telefoon: tik op "Horloge bijwerken" (of op de update-knop van de telefoon). Het
horloge zoekt dan zelf de nieuwste versie en installeert die op de achtergrond. De eerste keer
vraagt het horloge om bevestiging via een melding; daarna kan het zonder, omdat de app dan zelf
de installateur is. De telefoon toont wat het horloge terugmeldt.

Alle builds worden ondertekend met `app/padelscore.keystore`, zodat een nieuwe versie over de
oude heen installeert. Niet weggooien: een andere sleutel betekent eerst de app verwijderen.

## Installeren op het horloge
1. Horloge: Instellingen > Systeem > Info > tik 7x op Build-nummer.
2. Ontwikkelaarsopties: zet ADB-foutopsporing en Foutopsporing via wifi aan.
3. Pc: `adb connect <ip-van-horloge>:<poort>` en daarna `adb install app-debug.apk`.
