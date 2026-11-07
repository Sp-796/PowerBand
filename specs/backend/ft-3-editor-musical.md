---
id: ft-3
layer: backend
status: approved
---

# Editor Musical

## General description

- Grabar en tiempo real los comandos enviados al instrumento activo y reproducirlos posteriormente.
- Decorar `SynthCommand` con un adaptador de captura que registre los comandos y los delegue a la implementación real, sin acoplar el editor a un sintetizador concreto.
- Cargar una canción seleccionada desde el repositorio y reproducir sus eventos en orden, respetando sus tiempos relativos y enviándolos al `Instrument` configurado.

## Inputs

- Comandos `noteOn(midiNote, velocity)` y `noteOff(midiNote)` enviados al instrumento activo.
- Solicitudes para iniciar y detener una grabación.
- `songName` de la canción seleccionada para buscar en el índice y reproducir.
- `Instrument` de reproducción, construido con una implementación de `SynthCommand`.

## Outputs

- Eventos grabados con su desplazamiento temporal desde el inicio, orden de emisión y argumentos del comando.
- Canción persistida mediante el contrato `SongRepository`.
- Estado de grabación y reproducción; errores explícitos de persistencia, canción no encontrada o fallo del sintetizador.

## Test criteria

- Iniciar y detener la grabación abre y cierra una sola sesión; cada comando enviado al instrumento activo se captura con tiempo monotónico relativo al inicio y conserva su orden de emisión.
- El adaptador de captura delega cada comando a la implementación real de `SynthCommand` con los mismos argumentos y no captura comandos ajenos a la sesión activa.
- Los comandos emitidos fuera de una sesión de grabación no se agregan a la canción.
- Si falla la escritura de un evento o la finalización de la sesión, el error se informa y la grabación no se presenta como guardada correctamente.
- Al seleccionar un nombre, el servicio solicita sus eventos al repositorio en orden y los reproduce mediante el `Instrument` recibido.
- Cada evento se ejecuta en su desplazamiento temporal relativo; eventos con el mismo desplazamiento conservan su orden de emisión.
- La carga y reproducción funcionan como un Buffer en tiempo real, leyendo bloques de comandos mediante `RandomAccessFile` sin materializar la canción completa en memoria; detener la reproducción cancela la espera y la lectura de eventos pendientes.
- Un nombre inexistente produce un error explícito; los errores del repositorio o del sintetizador se propagan y no se reporta reproducción exitosa.

## Assets

- `RecordedCommand`: desplazamiento temporal (`long`), secuencia (`long`), tipo (`NOTE_ON` o `NOTE_OFF`), `midiNote` (`int`) y `velocity` (`int`, para `NOTE_ON`).
- Búsqueda por Nombre: la selección de la canción se hace mediante el `songName`, utilizando la búsqueda binaria sobre el archivo físico `recordings.index` definida en el Motor de Almacenamiento.
- `RecordingSynthCommand`: decorador de `SynthCommand` que captura los comandos emitidos durante la sesión activa y los delega al implementor real.
- `SongRepository`: puerto para iniciar escritura incremental de una canción y abrir un lector incremental de eventos a partir del offset obtenido del índice. La implementación física y el formato de disco pertenecen a la capa database.
- La política de validación MIDI y el catálogo de instrumentos quedan fuera del alcance de este spec.

## Implementation guidelines

- Implementar el dominio y la coordinación en `src/main/java/<package>/model/` y `src/main/java/<package>/service/`; ubicar contratos de entrada/salida en `src/main/java/<package>/dto/` y el puerto `SongRepository` en `src/main/java/<package>/repository/` cuando aplique.
- Usar un reloj monotónico (`System.nanoTime`) para medir intervalos; no usar la hora de pared para programar eventos.
- Mantener separado el servicio de grabación/reproducción de la persistencia concreta. Los lectores y escritores deben procesar eventos incrementalmente y liberar recursos al detenerse o fallar.
- Delegar la reproducción a los 3 instrumentos físicos definidos y a su interfaz `SynthCommand`, enviando los comandos finales hacia SuperCollider respetando la latencia exacta de los timestamps originales.
- Manejar I/O en la implementación del repositorio con `try-catch` y excepciones personalizadas; no ocultar errores ni devolver éxito por defecto.
- Seguir las convenciones Java del proyecto, mantener campos `private`, un tipo público por archivo y preferir la biblioteca estándar.

## Business workflow

1. Al iniciar grabación, abrir una sesión incremental de escritura y registrar el instante monotónico de inicio.
2. Por cada comando enviado al instrumento activo, el decorador captura el comando con su tiempo relativo y secuencia, lo delega a la implementación real y la sesión lo persiste incrementalmente.
3. Al detener la grabación, finalizar la escritura y confirmar el guardado solo si el repositorio cerró la canción correctamente.
4. Al elegir una canción, localizar su offset en disco por `songName` y abrir mediante `SongRepository` un lector incremental; si no existe, informar el error sin iniciar reproducción.
5. Leer cada evento en orden, esperar su desplazamiento temporal mediante un mecanismo interrumpible y despacharlo como `noteOn` o `noteOff` al `Instrument` configurado.
6. Al terminar, detener la reproducción y cerrar el lector; ante cancelación o error, cancelar eventos pendientes, liberar recursos e informar el resultado.
