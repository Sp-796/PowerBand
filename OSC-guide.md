# Guía rápida OSC

## Conexión

- En Open Stage Control, configura `send` como `127.0.0.1:57121`.
- Inicia PowerBand y SuperCollider. En SuperCollider, carga `PowerBandReceiver.scd`; debe aparecer `PowerBand OSC ready`.
- En Open Stage Control, desactiva **Editor → Enabled** para usar los controles.

## Botón `noteOn`

Agrega **Add widget → Basic → button** y configura:

- **Button → mode:** `tap`
- **OSC → address:** `/noteOn`
- **OSC → preArgs:** `[60]`
- **OSC → typeTags:** `ii`
- **Value → value:** `100`

## Botón `noteOff`

Agrega otro botón y configura:

- **Button → mode:** `tap`
- **Button → on:** `60`
- **OSC → address:** `/noteOff`
- **OSC → preArgs:** vacío
- **OSC → typeTags:** `i`

Toca `noteOn` para iniciar la nota y `noteOff` para detenerla. `typeTags` va sin coma inicial: `ii` y `i`.