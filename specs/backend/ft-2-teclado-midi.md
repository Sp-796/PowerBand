---
id: ft-2
layer: backend
status: draft
---

# Listener de teclado MIDI

## General description

- Capturar mensajes de nota de un dispositivo MIDI de entrada y convertirlos a llamadas del Bridge `ft-1`.
- Este feature cubre únicamente el teclado MIDI, su selección y su ciclo de vida; no configura SuperCollider ni la aplicación completa.

## Dependencies

- `ft-1`: `MidiKeyboardInstrument` extiende `Instrument` y recibe `SynthCommand`.

## Inputs

- Mensajes `javax.sound.midi.ShortMessage` recibidos desde un dispositivo con transmisor.
- Propiedad `powerband.midi.deviceName`; vacía selecciona el primer input válido.

## Outputs

- `NOTE_ON` con velocidad mayor que cero -> `noteOn(note, velocity)`.
- `NOTE_OFF` y `NOTE_ON` con velocidad cero -> `noteOff(note)`.
- API `startListening()`, `stopListening()`, `close()` y `listenerFailure()`.

## Test criteria

- Si se configura nombre, se elige el dispositivo con nombre exactamente igual; si no, el primer dispositivo que no sea `Sequencer` ni `Synthesizer` y tenga transmisor.
- Si no hay input válido o no se puede abrir, el inicio falla explícitamente y cierra todo recurso adquirido parcialmente.
- Solo se procesan `ShortMessage`; otros tipos y comandos MIDI se ignoran.
- Un evento válido se delega una vez y sin encolar una segunda tarea de despacho.
- Se mantiene el estado de las notas activas. Detener o cerrar envía `noteOff` por cada nota aún activa antes de cerrar receptor, transmisor y dispositivo.
- `stopListening()`/`close()` son idempotentes cuando no hay listener activo; iniciar una segunda vez mientras está activo falla.
- Los fallos asíncronos de delegación se registran como `HardwareInputException` en `listenerFailure()`; el inicio exitoso limpia el fallo anterior.

## Assets

- JDK `javax.sound.midi`; no requiere dependencia Maven externa.
- Nombre configurable mediante `powerband.midi.deviceName`; valor predeterminado vacío.

## Implementation guidelines

- Implementar/reutilizar `src/main/java/powerband/model/MidiKeyboardInstrument.java` y `HardwareInputException.java`.
- Extender `Instrument`, recibir el implementor por constructor e implementar `AutoCloseable`.
- No crear un sintetizador ni un `Receiver` de audio del JDK: delegar los eventos MIDI de entrada al Bridge.

## Business workflow

1. Construir el listener con el `SynthCommand` de `ft-1`.
2. Al iniciar, localizar un input según la propiedad, abrirlo, obtener su `Transmitter` y conectar el receptor de eventos.
3. Convertir únicamente los mensajes NOTE_ON/NOTE_OFF según las reglas de salida y delegarlos a `Instrument`.
4. Al detenerse, desactivar callbacks, liberar notas sostenidas y cerrar receptor, transmisor y dispositivo.
