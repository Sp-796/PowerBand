---
id: ft-5
layer: backend
status: draft
---

# Salida OSC hacia SuperCollider

## General description

- Implementar el adaptador concreto `SynthCommand` que traduce llamadas Bridge a datagramas OSC para SuperCollider.
- Esta etapa conecta la salida del backend; el servidor y el archivo `.scd` son software externo y no se crean aquí.
- Se implementa después de definir el contrato y las tres entradas de hardware, para conectar todos los eventos a un canal de sonido común.

## Dependencies

- Contrato de `ft-1`.
- Orden de construcción: completar y validar `ft-2`, `ft-3` y `ft-4` antes de integrar esta salida.

## Inputs

- `synthId` (predeterminado `0`), `powerband.osc.host` (`127.0.0.1`), `powerband.osc.port` (`57120`).
- Direcciones `powerband.osc.noteOnAddress` (`/noteOn`) y `powerband.osc.noteOffAddress` (`/noteOff`).
- Llamadas `noteOn(midiNote, velocity)` y `noteOff(midiNote)` del Bridge.

## Outputs

- UDP OSC note-on: path configurado, type-tag `,iii`, enteros `(synthId, midiNote, velocity)`.
- UDP OSC note-off: path configurado, type-tag `,ii`, enteros `(synthId, midiNote)`.
- `SynthCommandException` para resolución de host, socket cerrado o fallo local de envío. UDP no confirma recepción.

## Test criteria

- Rechazar ID negativo, host nulo/vacío y puerto fuera de `1..65535`.
- Codificar strings OSC en UTF-8 con NUL y padding a múltiplos de 4; codificar enteros big-endian.
- Enviar a la dirección/puerto configurados con path y argumentos exactos.
- No implementar ACK, reintento ni garantía de entrega; no reportar fallos locales como envío exitoso.
- `close()` libera el socket; cada envío posterior falla con excepción explícita.

## Assets

- Mensajes OSC de salida definidos arriba; deben coincidir con el handler configurado en el `.scd` que ejecuta el usuario.
- Propiedades y valores predeterminados centralizados en `powerband.config.Constants`.

## Implementation guidelines

- Implementar/reutilizar `src/main/java/powerband/model/SuperColliderSynthCommand.java`.
- Implementar `SynthCommand` de `ft-1`; no duplicar el codificador ni crear sockets dentro de los listeners.
- No incluir servidor SuperCollider, SynthDef, sintetizador de audio Java ni archivos WAV.

## Business workflow

1. Resolver configuración y crear un socket UDP.
2. Para cada método, construir el paquete OSC del path/type-tag correspondiente y enviar al host/puerto.
3. Propagar cualquier error local como `SynthCommandException`.
4. Al cierre de la aplicación, cerrar una vez el socket y rechazar envíos posteriores.
