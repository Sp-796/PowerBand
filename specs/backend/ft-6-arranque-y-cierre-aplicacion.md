---
id: ft-6
layer: backend
status: approved
---

# Arranque y cierre de PowerBand

## General description

- Ensamblar en `Main` el adaptador de salida y los tres instrumentos implementados en `ft-2` a `ft-5`.
- Iniciar la captura, mantener el proceso hasta ENTER y liberar todos los recursos en apagado normal o fallo parcial.
- No agregar interfaz de grabación/reproducción ni nuevas órdenes de consola en este feature.

## Dependencies

- `ft-2`: teclado MIDI.
- `ft-3`: gamepad.
- `ft-4`: entrada OSC móvil.
- `ft-5`: salida OSC a SuperCollider.

## Inputs

- Configuración de dispositivos y red de `powerband.config.Constants`.
- ENTER o EOF en stdin para solicitar cierre.

## Outputs

- Estado operativo: los tres listeners activos y compartiendo una única instancia `SuperColliderSynthCommand`.
- Mensaje de estado listo tras el inicio exitoso.
- Al salir, listeners detenidos y cerrados antes de cerrar el socket OSC de salida.
- Fallos de inicio/cierre informados sin declarar éxito ni dejar recursos parcialmente activos.

## Test criteria

- `Main.main` crea un solo `SuperColliderSynthCommand` y construye `GamepadInstrument`, `MidiKeyboardInstrument` y `MobileOscInstrument` con esa misma instancia.
- Si falla la construcción de cualquier recurso después de crear el synth o algún listener, cerrar los recursos ya creados y propagar el error; no iniciar listeners antes de completar la construcción.
- Iniciar en orden gamepad, MIDI y móvil; no esperar ENTER hasta que los tres `startListening()` terminen correctamente.
- Si un inicio falla, no esperar consola; intentar `stopListening()` y `close()` en los tres instrumentos, luego cerrar el synth y reportar el error original más errores de limpieza.
- Tras el inicio completo, mantener el hilo principal leyendo stdin hasta ENTER o EOF. La lectura no bloquea los callbacks/hilos de hardware.
- ENTER o EOF inicia cierre en orden: `stopListening()` de todos; `close()` de todos; finalmente `SuperColliderSynthCommand.close()`. Una falla individual no omite los intentos restantes.
- Cerrar el recurso de lectura de consola al salir. Reportar errores de cleanup después de intentar liberar todo.
- Ningún listener queda iniciado si `main` retorna por fallo o por cierre normal.

## Assets

- Punto de entrada `src/main/java/powerband/Main.java`.
- Clases de `powerband.model`: tres instrumentos concretos y `SuperColliderSynthCommand`.
- No crear un servicio de grabación, una UI o comandos `record/play/stop` para consola.

## Implementation guidelines

- Ensamblar recursos solo en `Main`; no mover el orden global de arranque a un listener.
- Usar una única instancia compartida de salida. Gestionar recursos con `try/finally` o estructura equivalente que intente todas las liberaciones.
- Construir todos los recursos antes de iniciar el primer listener, de modo que un error de configuración no deje parte del hardware activo.
- Los `stopListening()`/`close()` individuales deben tolerar el estado no iniciado y ser idempotentes.
- Propagar o presentar explícitamente el error principal; conservar errores de limpieza como suprimidos o reportados.

## Business workflow

1. Crear la salida SuperCollider y los tres listeners compartiendo el mismo `SynthCommand`.
2. Iniciar gamepad, MIDI y OSC móvil en el orden especificado.
3. Ante fallo de inicio, saltar la espera de consola y ejecutar la limpieza completa.
4. Si todo inicia, informar que está listo y esperar ENTER/EOF.
5. Detener los tres listeners, cerrar cada uno, cerrar el synth y la entrada de consola; informar fallos sin interrumpir el resto del cleanup.
