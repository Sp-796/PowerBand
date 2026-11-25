---
id: ft-9
layer: backend
status: approved
---

# Reproducción de grabaciones

## General description

- Leer incrementalmente una canción de `ft-7` y enviar sus eventos a un `Instrument` ya construido.
- Programar cada evento con su timestamp relativo mediante el servicio asíncrono existente.
- No implementar selector visual/consola, controles de velocidad, pausa, seek ni mezcla.

## Dependencies

- `ft-1`: `Instrument`.
- `ft-7`: `SongRepository`, `RecordingReader` y tipo de dominio `Command`.
- `ft-8`: grabaciones creadas por el flujo de captura.

## Inputs

- `MusicEditorService.play(String songName, Instrument instrument)`.
- `stopPlayback()`.
- Eventos leídos: `elapsedNanos`, tipo, nota y velocity. `sequence` se mantiene en el registro; `synthId` no selecciona el destino.

## Outputs

- `CompletableFuture<Void>`: normal al completar toda la canción; excepcional ante canción ausente, lectura, envío o cancelación.
- Llamadas `noteOn`/`noteOff` al instrumento suministrado en orden físico del reader.

## Test criteria

- Servicio cerrado, instrumento nulo o reproducción con future activa se rechazan sincrónicamente. Después de cancelar, la future se completa de inmediato; una nueva solicitud puede encolarse en el executor de un hilo mientras el worker anterior termina su cleanup, pero sus tareas no se ejecutan simultáneamente.
- Rechazos de API o doble reproducción producen `PlaybackException`; detener produce `PlaybackCancelledException`.
- La canción se abre en el worker asíncrono; fallo de búsqueda/lectura completa la future con excepción. Una grabación vacía completa normalmente sin enviar notas.
- Leer y despachar un comando a la vez; nunca cargar todos los comandos en memoria.
- Fijar `playbackStart` después de abrir reader. Esperar con reloj monotónico e interrupción hasta que transcurra `elapsedNanos` desde ese inicio; no sumar demoras evento-a-evento.
- Despachar en orden de lectura, incluyendo el orden original de comandos con timestamp igual; comandos atrasados se envían inmediatamente. No reordenar por timestamp ni `sequence`.
- Note-on llama `instrument.noteOn(note, velocity)`; note-off llama `instrument.noteOff(note)`. Ignorar `synthId` para escoger el instrumento destino.
- El tiempo es best-effort: no enviar antes del plazo objetivo; no prometer latencia máxima de tiempo real.
- `stopPlayback()` completa la future con `PlaybackCancelledException` e interrumpe el worker. Una llamada al instrumento ya en curso puede terminar; la cancelación interrumpe una espera temporal. Si los timestamps restantes ya vencieron, el worker puede despachar eventos listos antes de completar su cleanup; no se garantiza cancelación inmediata a nivel de nota.
- Cerrar reader en éxito, cancelación y error; fallos de lectura/instrumento se reflejan en la future y no como éxito.
- Usar un executor de un hilo daemon `powerband-playback`; no permitir más de una reproducción activa.

## Assets

- `MusicEditorService` en `src/main/java/powerband/service/`.
- `RecordingReader`/`SongRepository` de `ft-7` y `Instrument` de `ft-1`.

## Implementation guidelines

- Mantener la reproducción desacoplada de listeners físicos y transporte concreto.
- Usar `System.nanoTime()` o reloj monotónico inyectado y espera interrumpible en nanosegundos.
- Abrir reader dentro del worker con cierre garantizado; propagar causas a la `CompletableFuture`.
- El llamador selecciona el nombre y el instrumento; no iniciar automáticamente un listener ni agregar UI.

## Business workflow

1. Validar estado del servicio, instrumento y ausencia de reproducción activa; crear future y enviar tarea al executor.
2. Abrir reader por nombre y fijar hora monotónica de inicio.
3. Leer un comando; esperar hasta su timestamp absoluto relativo al inicio si aún está en el futuro.
4. Despachar note-on/off al instrumento en el orden leído y repetir.
5. Al EOF, cerrar reader y completar normalmente; ante stop, cancelación o error, cerrar y completar con la causa correspondiente.
