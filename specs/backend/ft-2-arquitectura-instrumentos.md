---
id: ft-2
layer: backend
status: approved
---

# Arquitectura de Instrumentos

## General description

- Separar la abstracción de instrumentos de la ejecución de comandos de sintetizador usando Bridge.
- `Instrument` delega las operaciones musicales a una implementación `SynthCommand`, recibida por composición; los instrumentos concretos corresponderán a hardware físico integrado al sistema.
- Permitir combinar una abstracción de instrumento con distintas implementaciones de sintetizador sin acoplar sus clases concretas.

## Inputs

- Operaciones musicales solicitadas sobre un `Instrument`, como iniciar y detener una nota MIDI (`midiNote`, `velocity`).
- Una implementación de `SynthCommand` inyectada al crear el instrumento.

## Outputs

- Comandos delegados por el `Instrument` a su `SynthCommand`, preservando nota, velocidad y orden de invocación.
- Resultado o excepción de la implementación del sintetizador, propagados por la abstracción sin simular éxito.

## Test criteria

- `Instrument` mantiene una referencia a la abstracción `SynthCommand`, no a una clase concreta de sintetizador.
- Iniciar y detener notas en un instrumento invoca los métodos correspondientes de `SynthCommand` con los mismos argumentos.
- Dos implementaciones distintas de `SynthCommand` pueden usarse con la misma abstracción de instrumento sin cambiarla.
- Una nueva abstracción de instrumento puede reutilizar implementaciones existentes de `SynthCommand`; los instrumentos específicos corresponden a los 3 periféricos físicos definidos.
- Los errores lanzados por una implementación de comando no se silencian ni se convierten en resultados exitosos.
- Las clases públicas respetan un tipo por archivo y las convenciones de nombres Java del proyecto.

## Assets

- Contrato conceptual:
  - `Instrument`: abstracción que recibe un `SynthCommand` y delega operaciones musicales.
  - `SynthCommand`: implementor con operaciones comunes `noteOn(int midiNote, int velocity)` y `noteOff(int midiNote)`.
  - Instrumentos concretos (Hardware físico): 1. Controlador/Teclado MIDI conectado vía USB, 2. Dispositivo móvil (ej. Android) enviando eventos de sensores (giroscopio/acelerómetro) vía red OSC, 3. Controlador de videojuegos (Gamepad/Joystick tipo Xbox/PlayStation) conectado vía USB/Bluetooth mapeando botones y ejes analógicos.
  - Implementaciones concretas de comandos de sintetizador: una clase por implementación, conectada a su sintetizador correspondiente.
- Las restricciones MIDI, el catálogo de instrumentos y la selección concreta de sintetizadores requieren definición del proyecto antes de añadir comportamientos específicos.

## Implementation guidelines

- Implementar el modelo Bridge en `src/main/java/<package>/model/`; ubicar coordinación de casos de uso en `src/main/java/<package>/service/` y tipos de entrada/salida en `src/main/java/<package>/dto/` solo si son necesarios.
- Mantener `Instrument` independiente de implementaciones concretas: recibir `SynthCommand` por constructor y delegar mediante la interfaz.
- Mantener cada abstracción e implementación pública en su propio archivo, con paquetes en minúsculas, clases `PascalCase`, métodos y campos `camelCase`, campos `private` y constantes `UPPER_SNAKE_CASE`.
- Usar Java y la versión de JDK declarada en el build del proyecto; preferir la biblioteca estándar y no agregar dependencias sin necesidad.
- Estrictamente prohibido el hardcoding. Todos los puertos de red (OSC), índices de controladores USB (Gamepad/MIDI), direcciones IP y configuraciones de conexión de los instrumentos físicos deben consumirse obligatoriamente desde una clase global `AppConstants.java` o `Constants.java`.

## Business workflow

1. El código de backend selecciona una implementación de `SynthCommand` y la proporciona a la abstracción `Instrument`.
2. El consumidor solicita una operación musical a `Instrument`, sin conocer el tipo concreto de sintetizador.
3. `Instrument` valida los argumentos comunes definidos por el dominio y delega la operación al método correspondiente de `SynthCommand`.
4. La implementación de comando ejecuta la operación en su sintetizador y devuelve el resultado o propaga el error al consumidor.
