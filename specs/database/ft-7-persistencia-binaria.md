---
id: ft-7
layer: database
status: approved
---

# Persistencia binaria de grabaciones

## General description

- Persistir las canciones grabadas en `recordings.music` y su índice en `recordings.index`.
- Proveer lectura/escritura incremental a `ft-8` y `ft-9`; no capturar hardware, despachar audio ni implementar UI.
- Definir el registro de dominio `Command` que compartirán captura, persistencia y reproducción.
- Construir esta capacidad después de validar el flujo en vivo de `ft-6`.

## Dependencies

- Sin dependencia de código directa del Bridge: los datos persistidos son valores MIDI y tiempos.
- Orden de proyecto: iniciar después de `ft-6`, para no empezar almacenamiento antes de tener el flujo de instrumentos operativo.

## Inputs

- Título `songName`.
- Evento con `elapsedNanos(long)`, `sequence(long)`, `synthId(int)`, `type(NOTE_OFF|NOTE_ON)`, `midiNote(int)` y `velocity(int)`.
- Solicitud de lectura por nombre.

## Outputs

- `SongRepository.beginRecording(String)` -> `RecordingWriter`.
- `SongRepository.openReader(String)` -> `RecordingReader`.
- Excepciones específicas para formato/nombre inválido, ausencia de canción, corrupción e I/O.
- Tipos de error: `RecordingFormatException` para título/comando/registro inválido, `IndexCorruptedException` para índice u offset corrupto, `RecordingNotFoundException` para búsqueda sin resultado y `StorageException` para errores de disco.
- `RecordingReader.readNext()` devuelve un comando por llamada y `null` solo al terminar los comandos declarados.
- Tipo de dominio `Command` en `src/main/java/powerband/model/`, con los seis campos del evento y validación de sus rangos.

## Test criteria

- Predeterminados `TITLE_BYTES=256`, `MAX_COMMANDS_PER_RECORD=4096`; configurables por `powerband.storage.titleBytes` y `powerband.storage.maxCommands`.
- Anchos: count 4, offset 8, timestamp 8, secuencia 8, synth ID 4, tipo 1, nota 4, velocidad 4 bytes. Comando = 29 bytes; enteros big-endian.
- `recordings.music`: `TITLE_BYTES + 4 + MAX_COMMANDS_PER_RECORD*29` bytes por registro fijo. `recordings.index`: `TITLE_BYTES + 8` bytes por entrada fija.
- El título es UTF-8 estricto, no vacío, sin NUL, máximo `TITLE_BYTES` bytes; se rellena con ceros, no se trunca ni normaliza. Bytes no cero tras el primer NUL son corrupción.
- Tipo válido: NOTE_OFF=`0`, NOTE_ON=`1`; timestamps, secuencia, synth ID, nota y velocidad deben satisfacer las restricciones de `Command` (no negativos para tiempo/secuencia/ID; nota y velocidad `0..127`).
- Aceptar grabación vacía con `commandCount=0`. Rechazar append que supere la capacidad; no aumentar el slot dinámicamente.
- Al iniciar escritura, reservar un registro fijo en EOF alineado e inicializar el count en cero. Cada append escribe los 29 bytes del siguiente slot. Al cerrar correctamente, escribir count y luego añadir índice.
- Si la grabación falla antes de indexarse, no publicar su entrada; el slot reservado puede quedar huérfano al final de `recordings.music` y no se reutiliza automáticamente.
- Permitir títulos repetidos. Orden total: clave natural NFD/minúsculas `Locale.ROOT`/sin marcas; dígitos ASCII comparados numéricamente ignorando ceros iniciales; desempate por título original `String.compareTo`; luego `musicOffset` ascendente.
- En la clave natural, caracteres no numéricos se comparan como `char` Java; si una clave termina antes, va primero. Por ejemplo, `Tema 2` antes de `Tema 10`; `Tema 02` empata naturalmente con `Tema 2` y pasa al desempate por título original.
- Búsqueda binaria usa solo la clave natural. Nombres equivalentes por mayúsculas, marcas o valor numérico seleccionan la primera entrada en el orden total.
- Buscar directamente el archivo índice. Ordenar con Balanced Merge Sort físico, dos archivos temporales configurados y memoria acotada a buffers/registros; nunca cargar archivo completo.
- Rechazar longitudes no alineadas, nombres o comandos inválidos, offsets negativos/no alineados/fuera de rango y entradas corruptas con errores explícitos.

## Assets

- Registro de música: `songName[TITLE_BYTES] | commandCount[4] | commandSlots[MAX_COMMANDS_PER_RECORD*29]`.
- Registro de índice: `songName[TITLE_BYTES] | musicOffset[8]`, offset absoluto al comienzo del registro en `recordings.music`.
- Propiedades:
  - `powerband.recordings.file` = `recordings.music`.
  - `powerband.index.file` = `recordings.index`.
  - `powerband.index.tempA` = `recordings.index.tmp-a`.
  - `powerband.index.tempB` = `recordings.index.tmp-b`.
  - `powerband.storage.titleBytes` = `256`; `powerband.storage.maxCommands` = `4096`.
- Las cuatro rutas deben ser distintas.

## Implementation guidelines

- Usar `src/main/java/powerband/repository/` para `SongRepository`, `RecordingWriter`, `RecordingReader` y `BinaryRecordingRepository`; configuración en `powerband.config.Constants`.
- Definir/reutilizar `src/main/java/powerband/model/Command.java` como dato persistible; no hacerlo depender de listeners o de `SynthCommand`.
- Usar acceso aleatorio/secuencial estándar y memoria acotada. No usar serialización Java nativa ni ordenar con arreglos/colecciones en memoria.
- Mantener exactamente los contratos nombrados en Outputs. Cerrar recursos y propagar excepciones de dominio; no reportar éxito tras I/O fallido.
- No implementar borrado, reemplazo, renombrado, recuperación automática o migración de formatos.

## Business workflow

1. Validar título y que las rutas configuradas sean distintas.
2. Validar alineación de archivos y reservar un registro fijo al final de música.
3. Escribir comandos incrementalmente y rechazar el siguiente append una vez alcanzado el límite.
4. En cierre exitoso, escribir el count, cerrar el registro, anexar índice y ordenar el índice físicamente.
5. Para lectura, validar el índice, localizar por clave natural, validar el offset y abrir un reader limitado al count.
