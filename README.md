# ART STAR 1.4

App Android nativa en Kotlin de DigitalMinds para aprender y practicar las marchas de la banda escolar. Sin internet, sin anuncios y sin dependencias externas.

## Funciones

- Partituras que se desplazan con la digitación encima de cada nota: pistones para bajo/barítono, trompeta y tuba; llaves para saxo alto; posición de vara para trombón. Trombón y tuba se leen en clave de fa.
- Marchas: Cóndor Pasa, Triste Payaso, Jinetes en el Cielo, Colegiala y Cholita.
- Banda de acompañamiento: los demás instrumentos de viento de la marcha suenan con muestras reales, más bombo, tarola, napoleón, platillo, pandereta y lira, cada uno con 3 formas de tocar.
- Velocidades en BPM reales, modo Espera y modo Evaluar con micrófono, secciones y bucle.
- Aprender: explorador de notas con flechas, símbolos uno por uno, tablas de digitación con sonido, ejercicios y 16 canciones para bajo, trompeta, saxo, trombón y tuba.
- Metrónomo con subdivisiones y afinador con transposición (Si bemol y Mi bemol).

## Compilar

Android Studio (abrir esta carpeta y Run) o `./gradlew assembleRelease`. El flujo de GitHub Actions está en la raíz del repositorio y busca solo la carpeta del proyecto.

## Partituras

- `tools/songs/*.txt`: marchas. `tools/lessons/*.txt`: ejercicios y canciones.
- Compilar a la app: `python3 tools/build_songs.py tools/songs tools/lessons app/src/main/assets/songs` (valida que cada compás sume exacto).
- Revisión de armonía entre partes: `python3 tools/check_harmony.py condor.txt` (desde `tools`).
- Sonidos: `tools/build_sounds.py` convierte las muestras FluidR3 GM (CC BY 3.0).
