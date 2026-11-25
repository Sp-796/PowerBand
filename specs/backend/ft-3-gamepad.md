---
id: ft-3
layer: backend
status: draft
---

# Listener de gamepad

## General description

- Leer transiciones de botones de un gamepad/stick JInput y mapear cada botón a una nota MIDI.
- No leer ejes analógicos ni agregar una capa de grabación.

## Dependencies

- `ft-1`: `GamepadInstrument` extiende `Instrument` y delega al `SynthCommand` inyectado.
- `ft-2`: reutiliza `HardwareInputException`, la configuración central y el contrato de ciclo de vida establecido por el listener MIDI.

## Inputs

- Controladores JInput de tipo `GAMEPAD` o `STICK`.
- Propiedades:
  - `powerband.gamepad.name` (vacía: primer controlador compatible).
  - `powerband.gamepad.baseNote` (60), `powerband.gamepad.buttonNoteStep` (1).
  - `powerband.gamepad.buttonVelocity` (100), `powerband.gamepad.pollIntervalMillis` (8).
  - `powerband.gamepad.pressedThreshold` (0.5).

## Outputs

- Para botón de índice `i`, note-on `baseNote + i * buttonNoteStep` con velocidad configurada al presionar; note-off de esa nota al soltar.
- API `startListening()`, `stopListening()`, `close()` y `listenerFailure()`.

## Test criteria

- Seleccionar por coincidencia exacta de nombre cuando se configure; de lo contrario, el primer controlador GAMEPAD/STICK. Si no se encuentra, fallar explícitamente.
- Enumerar exclusivamente componentes Button, en el orden que devuelve el controlador; no crear eventos para ejes u otros componentes.
- `pollData >= pressedThreshold` significa presionado. Emitir solo en transiciones false->true / true->false; mantener estado por botón.
- Aplicar la fórmula de nota indicada; validar que base, velocidad y la última nota mapeada sean válidos en `0..127`, que el paso sea positivo, que `pollIntervalMillis > 0` y que el umbral esté en `0.0..1.0`. Rechazar mapeos que desborden.
- Sondear cada `pollIntervalMillis`; si `poll()` devuelve false, registrar `HardwareInputException` y terminar el hilo.
- Un dispositivo sin botones, dispositivo ausente, mapeo inválido o error al delegar no se trata como éxito.
- Al detener, interrumpir y esperar el hilo; enviar note-off a cada botón mantenido. Cierre repetido sin hilo activo es seguro.
- `listenerFailure()` devuelve `null` después de un inicio limpio y guarda el último `HardwareInputException` de sondeo/delegación; un inicio nuevo limpia el fallo anterior.

## Assets

- JInput `2.0.10`, ya declarado en `pom.xml`.
- Mapeo y valores predeterminados centralizados en `powerband.config.Constants`.
- Propiedades de configuración enumeradas en Inputs.

## Implementation guidelines

- Implementar/reutilizar `src/main/java/powerband/model/GamepadInstrument.java`.
- Mantener un hilo daemon de sondeo por instrumento y nombrarlo `powerband-gamepad-listener`.
- No iniciar hilos adicionales por botón ni enviar OSC desde esta clase.

## Business workflow

1. Validar propiedades de mapeo y localizar el controlador.
2. Enumerar botones y validar la nota máxima que producirá la fórmula.
3. Crear estado de botones e iniciar el hilo de sondeo.
4. En cada sondeo, detectar cambios y delegar inmediatamente la nota correspondiente.
5. Al detener o fallar, interrumpir el hilo, liberar las notas presionadas, guardar el fallo si existe y esperar el fin del hilo.
