# ART STAR 1.6

App Android nativa en Kotlin de DigitalMinds para aprender y practicar las marchas de la banda escolar. Sin internet, sin anuncios y sin dependencias externas.

## Funciones

- Partituras que se desplazan con la digitación encima de cada nota: pistones para bajo/barítono, trompeta y tuba; llaves para saxo alto; posición de vara para trombón. Trombón y tuba se leen en clave de fa.
- Marchas: Cóndor Pasa, Triste Payaso, Jinetes en el Cielo, Colegiala, Cholita y Túpac Amaru.
- Banda de acompañamiento: los demás instrumentos de viento de la marcha suenan con muestras reales, más bombo, tarola, napoleón, platillo, pandereta y lira, cada uno con 3 formas de tocar.
- Velocidades en BPM reales, modo Espera y modo Evaluar con micrófono, secciones y bucle.
- Aprender: explorador de notas con flechas, símbolos uno por uno, tablas de digitación con sonido, ejercicios y 10 canciones continuas (sin silencios) para bajo, trompeta, saxo, trombón y tuba.
- Botones de salto de 1 a 30 s, arrastre sobre la partitura y volumen propio para cada instrumento de la banda.
- Metrónomo con subdivisiones y afinador con transposición (Si bemol y Mi bemol).

## Compilar

Android Studio (abrir esta carpeta y Run) o `./gradlew assembleRelease`. El flujo de GitHub Actions está en la raíz del repositorio y busca solo la carpeta del proyecto.

## Partituras

- `tools/songs/*.txt`: marchas. `tools/lessons/*.txt`: ejercicios y canciones.
- Compilar a la app: `python3 tools/build_songs.py tools/songs tools/lessons app/src/main/assets/songs` (valida que cada compás sume exacto).
- Revisión de armonía entre partes: `python3 tools/check_harmony.py condor.txt` (desde `tools`).
- Sonidos: `tools/build_sounds.py` convierte las muestras FluidR3 GM (CC BY 3.0).

## Novedades 1.6

- Túpac Amaru (Nilton Calderón Torres): trompetas 1, 2 y 3, barítonos (eufonios) 1 y 2, saxos alto 1 y 2, trombones 1, 2 y 3 y tuba. Estructura completa: Segno, repeticiones con casillas, Fin y "Al Segno y Fin".
- Jinetes en el Cielo reemplazada por la transcripción del Tte. Mús. Ignacio Cumaly F. (trompetas 1 y 2, barítonos 1 y 2). El saxo alto viene de otro arreglo escrito una quinta más arriba: se transportó a la tonalidad de la banda sin cambiar notas ni ritmos. Trombón y tuba siguen adaptados (marcados así) porque no hay partitura de ellos.
- Triste Payaso: saxos alto 1 y 2, trombones 1 y 2 y tuba con su partitura propia (la tuba viene escrita para Si bemol y se bajó un tono).
- Cholita: saxos alto 1 y 2 (el 1 coincide con la trompeta 1 una quinta arriba). Trombón y tuba marcados como adaptados.
- Percusión: tarola "Marcha redoblada" (floreo en el 1 y negras) y bombo "Uno por compás" tomados de la partitura de marcha redoblada; tarola "Contratiempo" tomada de la batería de Túpac Amaru.
- Aprender: 10 canciones continuas, sin silencios, ajustadas al registro de cada instrumento; las anacrusas empiezan directo en la primera nota.
- Reproductor: saltos de 1 a 30 s, arrastre sobre la partitura, audio sin ruido al moverse, "Mi parte" (antes Guía), volumen propio para cada instrumento de la banda y ajustes con descripción.
- Afinador más estable, con Trombón y Tuba.
- Corregido el cierre al volver atrás en Aprender.
