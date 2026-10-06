# ART STAR

App Android nativa en Kotlin, de DigitalMinds, para aprender y practicar partituras de banda de marcha con el bajo (barítono de 3 pistones en Si bemol). Sin internet, sin anuncios, sin dependencias externas: solo el framework de Android.

## Qué hace

- **Partituras que se desplazan** de derecha a izquierda sobre un pentagrama real, con la digitación (0, 1, 2, 3) escrita encima de cada nota y una línea roja de "ahora".
- **5 velocidades**: 50 %, 70 %, 85 %, Real (100 %) y 115 %.
- **Metrónomo** con un compás de preparación, y metrónomo independiente (2/4, 3/4, 4/4, 6/8, toque de tempo).
- **Modo espera**: la partitura avanza cuando tocas la nota correcta (micrófono).
- **Modo evaluar**: tocas a tiempo y ganas de 0 a 3 estrellas.
- **Afinador** con aguja en centésimas, La de referencia ajustable y la nota que lees en la partitura (instrumento en Si bemol).
- **Aprender**: explorador de notas, tabla de pistones, glosario de símbolos (figuras, silencios, repeticiones, casillas, ligaduras, tresillos, segno, coda, matices) y 14 ejercicios, desde redondas hasta canciones.
- **Secciones de práctica** en cada marcha, con repetición en bucle.

## Compilar

Abre la carpeta en Android Studio y pulsa Run, o desde la terminal:

    ./gradlew assembleRelease

El APK queda en `app/build/outputs/apk/release/`. Está firmado con la clave de depuración, así que se instala directamente. También hay un flujo de GitHub Actions (`.github/workflows/build.yml`) que lo compila en la nube.

## Agregar o corregir partituras

Las partituras están en `tools/songs/*.txt` (marchas) y `tools/lessons/*.txt` (ejercicios). Después de editarlas:

    python3 tools/build_songs.py tools/songs tools/lessons app/src/main/assets/songs

El script valida que cada compás sume exactamente su duración. Formato de una nota: `duración:nota`, por ejemplo `e.:B4` (corchea con puntillo), `q:r` (silencio de negra), `h:E4~` (blanca ligada), `e3:G4` (corchea de tresillo). Duraciones: `w` redonda, `h` blanca, `q` negra, `e` corchea, `s` semicorchea, `t` fusa; `.` puntillo, `3` tresillo. Marcas por compás entre llaves: `rs` inicio de repetición, `re` fin, `v1`/`v2` casillas, `seg`, `coda`, `t=Texto`, `rm=A`.

## Notas técnicas

- La comparación con el micrófono usa la clase de altura (sin octava), así que funciona igual con bajo, barítono o trompeta.
- Instrumento en Si bemol: la nota escrita suena un tono más grave.
- Fuentes: Space Grotesk y Bravura (licencia OFL).
