---
id: ft-8
layer: backend
status: approved
---

# Grabación en tiempo real

## General description

- Decorar el `SynthCommand` real con `RecordingSynthCommand` para capturar eventos que ya atraviesan los listeners de `ft-2` a `ft-4`.
- Persistir comandos inmediatamente mediante `ft-7`, conservando el envío de audio en vivo.
- No implementar reproducción ni nuevos listeners/UI.

## Dependencies

- `ft-1`: `SynthCommand` e `Instrument`.
- `ft-2`, `ft-3`, `ft-4`, `ft-5` y `ft-6`: entradas, salida y composición operativa.
- `ft-7`: `SongRepository` y `RecordingWriter`.

## Inputs

- `MusicEditorService.startRecording(String songName)` / `stopRecording()`.
- `noteOn(midiNote, velocity)` y `noteOff(midiNote)` recibidos por el decorador.
- Reloj monotónico; `System.nanoTime()` en producción o `LongSupplier` inyectado.

## Outputs

- `Command` con tiempo relativo en nanosegundos, secuencia, ID del implementor, tipo, nota y velocidad.
- Estado `isRecording()` y fallos explícitos de almacenamiento.

## Test criteria

- Una sola sesión activa. Empezar durante grabación o detener sin sesión activa produce `StorageException`.
- `startRecording` abre writer; si falla, no activa sesión. Al iniciar, tiempo base es `nanoTime` y primera secuencia `0`.
- Cada llamada al decorador toma tiempo relativo al entrar, delega una vez al synth real y, solo si esa llamada termina correctamente, persiste el evento. El decorador sincroniza las operaciones para conservar orden de secuencia.
- `noteOff` se guarda con velocity `0`. Fuera de sesión se delega sin persistirse.
- Si falla la delegación, propagar el error y no guardar ese evento; la sesión permanece activa.
- Si append falla o excede capacidad, cerrar el writer defectuoso, dejar sesión inactiva, propagar `StorageException` y no indexar la grabación. `stopRecording()` posterior vuelve a reportar el fallo.
- `stopRecording()` cierra el writer; solo después del cierre exitoso existe grabación guardada. Un fallo de almacenamiento puede ocurrir después de que el sonido ya se envió y no se revierte.
- No acumular eventos en una lista, no capturar audio ni escribir WAV.

## Assets

- `RecordingSynthCommand` implementa `SynthCommand`; `MusicEditorService` coordina start/stop.
- `Command` usa los campos definidos por `ft-7`; formato y capacidad definidos por ese mismo feature.
- Wiring de runtime: los tres instrumentos deben recibir la misma instancia `RecordingSynthCommand(realSynth, repository)` cuando la grabación está habilitada; el decorador envuelve una sola instancia real de salida.

## Implementation guidelines

- Usar `src/main/java/powerband/service/RecordingSynthCommand.java` y `MusicEditorService.java`.
- Usar `RecordingWriter`/`SongRepository` de `powerband.repository`; no acoplarse a `BinaryRecordingRepository`.
- Al integrar con `Main`, crear un repositorio, envolver el `SuperColliderSynthCommand` compartido en un solo decorador y pasar el decorador a los tres listeners; crear `MusicEditorService` con el mismo repositorio/decorador.
- Al apagar, detener los listeners primero (sus note-off finales forman parte de la captura), cerrar `MusicEditorService` para finalizar una grabación abierta, y luego cerrar instrumentos y synth. No perder errores de guardado durante el cierre.
- No definir comandos de consola ni UI para empezar/parar grabación; son llamadas de servicio en este feature.

## Business workflow

1. Crear repositorio, synth real, un decorador de grabación compartido y `MusicEditorService`; pasar el decorador a los tres instrumentos.
2. `startRecording(name)` rechaza estado activo, registra tiempo monotónico y abre writer.
3. Cada evento válido llega desde el listener; registrar tiempo, delegar al synth real y anexar el comando después de una delegación exitosa.
4. Incrementar secuencia solo tras append exitoso.
5. `stopRecording()` cierra writer; informar éxito únicamente si `ft-7` actualizó el registro y el índice.
