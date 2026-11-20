# Lens Mind + Remote
Kamera → offline képfelismerés (ML Kit) → IR távirányító. "Vissza a jövőbe" időáramkör-dizájn.

## Telepítés
1. Új, **publikus** GitHub repó, töltsd fel ide a teljes tartalmat a `main` ágra.
2. Actions → "Build APK" lefut (vagy Run workflow). A bot Release-t készít az APK-val.
3. Telefonon: Releases → `app-debug.apk` → telepítés.
   (Ha korábbi, régi buildet telepítettél: egyszer töröld le, mert az aláírókulcs most változott.)

## Funkciók
- ⏻ 🔉 🔊 🔇 – IR távirányító (LG / Samsung / Sony), automata márkakeresés
- 🐞 Debug – napló, eszközinfó, összeomlás-napló, másolás/küldés
- QR – alkalmazás-letöltő link és beállítás-profil megosztása; a kamera beolvassa a profil-QR-t
- ⬆ FRISSÍTÉS – a legfrissebb Release-t tölti le és telepíti (új verziónál a gomb borostyánra vált)

## Megjegyzés
A repó neve automatikusan bekerül az appba (a workflow adja át), így a QR és a Frissítés gomb
a saját repódra mutat. Az aláírókulcs (app/lensmind.keystore) nyilvános, hobbi célra való.
