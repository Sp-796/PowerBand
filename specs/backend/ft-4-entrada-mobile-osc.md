---
id: ft-4
layer: backend
status: draft
---

# Listener OSC móvil

## General description

- Recibir eventos OSC del celular y convertirlos al contrato de notas de `ft-1`.
- El cliente móvil envía comandos de nota; acelerómetro/giroscopio y otros sensores quedan fuera del alcance.

## Dependencies

- `ft-1`: `MobileOscInstrument` extiende `Instrument` y delega al `SynthCommand`.
- `ft-2`: reutiliza `HardwareInputException` y el contrato de ciclo de vida/estado de fallo de los listeners.

## Inputs

- Puerto UDP `powerband.mobile.osc.port` (predeterminado `57121`).
- Paths `powerband.mobile.osc.noteOnAddress` (`/noteOn`) y `powerband.mobile.osc.noteOffAddress` (`/noteOff`).
- Mensajes de note-on con dos argumentos enteros `(midiNote, velocity)` y note-off con un argumento entero `(midiNote)`.

## Outputs

- Mensajes válidos delegados como `noteOn(midiNote, velocity)` o `noteOff(midiNote)`.
- API `startListening()`, `stopListening()`, `close()` y `listenerFailure()`.

## Test criteria

- Validar puerto en `1..65535`, paths OSC válidos y diferentes antes de enlazar el socket.
- Usar los paths configurados como selectores de dirección JavaOSC; si contienen patrones OSC, aceptar las direcciones que coincidan según JavaOSC. Los dos selectores configurados deben ser válidos y distintos. Validar cantidad exacta de argumentos y tipo `Integer`; rechazar nota/velocidad fuera de `0..127`.
- Un mensaje válido se delega una vez. Un mensaje inválido se registra como `HardwareInputException`, no se convierte en nota y no detiene la recepción posterior.
- No agregar `synthId` al mensaje entrante; ese dato corresponde al destino de `SynthCommand`.
- Al iniciar, limpiar fallo anterior. Si el bind o inicio falla, cerrar el puerto y propagar error explícito.
- Al detener/cerrar, cerrar el puerto y emitir note-off de cada nota sostenida; fallo de cierre se registra y se propaga. Cierre repetido sin puerto activo es seguro.

## Assets

- JavaOSC `0.8` (API disponible en `pom.xml`); no asumir versión `0.8.1`.
- `powerband.config.Constants` centraliza puerto y direcciones.
- `HardwareInputException` del feature `ft-2` es el tipo común de error para todos los listeners.

## Implementation guidelines

- Implementar/reutilizar `src/main/java/powerband/model/MobileOscInstrument.java`.
- Extender `Instrument`, usar el listener y dispatcher JavaOSC y preservar el cierre de recursos.
- No enviar OSC hacia SuperCollider desde esta clase; solo recibir OSC móvil y delegar al Bridge.

## Business workflow

1. Validar puerto y paths configurados.
2. Crear el puerto OSC de entrada, registrar selectores para ambos paths y comenzar la escucha.
3. Validar cada mensaje (path, cantidad, tipos y rangos) y delegarlo inmediatamente.
4. Registrar errores de mensaje/delegación sin terminar el receptor.
5. Al detenerse, cerrar el puerto y liberar todas las notas sostenidas.
