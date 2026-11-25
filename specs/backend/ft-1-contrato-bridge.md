---
id: ft-1
layer: backend
status: draft
---

# Contrato Bridge para comandos musicales

## General description

- Establecer el contrato común entre fuentes de entrada y un destino de comandos musicales.
- Definir `Instrument` como abstracción que recibe un `SynthCommand` por composición y delega `noteOn`/`noteOff`.
- No implementar todavía listeners, transporte OSC ni grabación.

## Dependencies

- Ninguna. Es el contrato base de los features siguientes.

## Inputs

- `SynthCommand`: `synthId()`, `noteOn(int midiNote, int velocity)` y `noteOff(int midiNote)`.
- Llamadas de nota hechas sobre `Instrument`.

## Outputs

- Delegación a la misma instancia `SynthCommand`, conservando argumentos y orden de llamadas.
- Error explícito para comando nulo o valores MIDI fuera de `0..127`.

## Test criteria

- `Instrument` acepta una implementación de `SynthCommand` en el constructor; un valor nulo se rechaza.
- `noteOn` valida nota y velocidad; `noteOff` valida la nota. Los extremos `0` y `127` son válidos.
- Cada llamada válida invoca exactamente una vez el método correspondiente en el implementor y conserva sus argumentos.
- Excepciones del implementor se propagan sin convertirse en éxito.
- `Instrument` no importa ni instancia implementadores concretos.

## Assets

- `Instrument` es la abstracción común; los instrumentos específicos extenderán esta clase.
- `SynthCommand` es la interfaz implementor; un sintetizador o decorador la implementa.

## Implementation guidelines

- Implementar/reutilizar `src/main/java/powerband/model/Instrument.java` y `SynthCommand.java`.
- Mantener la validación MIDI centralizada en `Instrument` y no incluir dependencias de hardware, OSC ni archivos.
- Usar el Java y target declarados en `pom.xml`; no agregar dependencias para este contrato.

## Business workflow

1. El consumidor construye un `Instrument` con una instancia de `SynthCommand`.
2. `Instrument` valida los argumentos del evento.
3. Si son válidos, delega exactamente una vez al implementor; si no, rechaza la llamada antes de delegar.
