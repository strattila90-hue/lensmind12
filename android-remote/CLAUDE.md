# Onda Haptic: emlékeztető, milyennek kell kinéznie és működnie

Ezt tartsd meg minden módosításnál. A forrás az `app/src/main/assets/index.html`.

## Kinézet
- **Térhatás:** a távirányítónak mélysége és magassága van. Vastag, lekerekített ház, kiemelkedő gombok, mély kerék és csúszkarés. A ház a kurzor felé dől, telefonon a dőlésszög szerint.
- **RGB fény:** a ház pereme szivárványszínekben körbefut. LED módban a kiválasztott színnel világít, és a TV és a háttér is átveszi.

## Tapintás
- **Rezgés:** a telefon rezeg a `OndaNative.haptic(kind)` hídon át (`MainActivity.java`).
- **Gombnyomás:** tompa puffanás, mély hang és erős, rövid rezgés.
- **Tekerés:** apró kattanások, halk, rövid hang és finom rezgés minden fokozatnál.

## Frissítés
Önálló frissítés: az app indításkor, utána 15 percenként, a háttérben pedig 3 óránként megnézi az új `onda-N` kiadást, letölti és telepíti. Android 12+ rendszeren az első kézi telepítés után megerősítés nélkül is lefuthat. Az aláírókulcs (`app/lensmind.keystore`) nyilvános, hobbi célra való.
