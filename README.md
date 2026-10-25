# Winner Padel (Wear OS)

Scoreteller voor padel op een Android-horloge (Wear OS 3 of nieuwer).

## Gebruik
- Startscherm: kies wat er bij 40-40 gebeurt (voordeel, gouden punt of star point: eerst voordeel, bij de derde deuce beslist het volgende punt), spelvorm (sets spelen of alleen games tellen), bij sets 1 set of best of 3, en wie begint met opslaan.
- Tik op de linkerhelft (Wij, blauw) of rechterhelft (Zij, rood) voor een punt.
- Wij staan (startscherm): kies of Wij links of rechts beginnen.
- Kant wisselen (startscherm of menu): aan = wisselen na game 1, 3, 5, ... en in de tiebreak na
  elke 6 punten. De vlakken schuiven dan mee (blauw blijft Wij), op horloge en telefoon, en de
  scheidsrechter roept "Kant wisselen". Uit = blijven staan.
- Gele stip = dit team serveert. Bij 6-6 volgt een tiebreak tot 7 (2 verschil).
- Tijdens de partij is de stand full screen. De terugknop opent het menu: verder spelen,
  laatste punt terug, bijwerken, gouden punt, scherm altijd aan en partij stoppen.
- Scherm altijd aan (startscherm): houdt het scherm fel aan tijdens de partij. Uit: het scherm
  dimt naar een zuinige zwart-wit stand (ambient mode) en een tik, knop of polsbeweging brengt je
  terug in de app. Tijdens een partij staat er een icoon op de wijzerplaat om terug te springen.
- De stand wordt opgeslagen, dus als de app wordt afgesloten gaat er niets verloren.

## Telefoon-app
Op je telefoon zie je de stand live, via de gewone koppeling tussen horloge en telefoon.
Zolang er een partij bezig is staat er ook een melding met de stand, en blijft het scherm
van de telefoon aan als de app open staat.
Download: `padelscore-telefoon.apk` bij de nieuwste release met "telefoon" in de naam op
https://github.com/micha35fey/apphorloge/releases

Score omroepen: de telefoon roept de stand om als een scheidsrechter, zonder teamnamen
("dertig, vijftien", "deuce", "voordeel"/"nadeel" vanuit de serveerder, "game", pauze, "drie, twee").
Bij de start van een partij: "Fijne wedstrijd. Maak er een mooie pot van".
Na "Game, set en match" volgt ongeveer 10 seconden applaus (zelf gegenereerd met
mobile/applause_gen.py, dus vrij te gebruiken). Met "Eigen applaus kiezen" gebruik je een
geluidsbestand van de telefoon; "Applaus testen" laat het horen.
Met "Stem wisselen" kies je een andere Nederlandse stem, met een voorbeeld.
Aan of uit met de schakelaar onderaan het telefoonscherm. Gebruikt het media-volume, dus ook via een
Bluetooth-speaker; muziek gaat even zachter tijdens de omroep en een korte stilte vooraf wekt de speaker.

Niet storen tijdens partij (telefoon): zet Niet storen aan zolang er op het horloge een partij
bezig is. Meldingen zijn stil; telefoontjes, wekkers en de scheidsrechter komen door. Daarna
komt je eigen instelling terug. De eerste keer vraagt Android om toegang tot Niet storen.

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
