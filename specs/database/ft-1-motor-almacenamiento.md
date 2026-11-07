---
id: ft-1
layer: database
status: draft
---

# Motor de Almacenamiento

## General description

- Persistir las grabaciones de canciones en `recordings.music` y sus entradas ordenadas en `recordings.index`.
- Usar registros binarios de tamaño fijo, acceso por desplazamiento y operaciones de disco en streaming; no cargar archivos completos ni ordenar índices completos en memoria.

## Inputs

- `Recording`: `songName` (`String`) y `audioLength` (`long`), recibidos del servicio.
- Flujo de audio (`InputStream`) y longitud declarada; debe caber en el tamaño máximo configurado.
- Nombre de canción para localizarla en el índice.

## Outputs

- Registro de grabación persistido y su `musicOffset` (`long`), que apunta al inicio del registro en `recordings.music`.
- `RecordingIndexEntry`: `songName` (`String`) y `musicOffset` (`long`), persistida en `recordings.index`.
- En lectura, metadatos de la grabación y flujo de audio acotado a `audioLength`.
- Errores explícitos de validación, corrupción o I/O mediante excepciones específicas; no informar éxito si la escritura falla.

## Test criteria

- Cada registro de `recordings.music` tiene exactamente `TITLE_BYTES + AUDIO_LENGTH_BYTES + AUDIO_SLOT_BYTES`; cada entrada de `recordings.index` tiene exactamente `TITLE_BYTES + OFFSET_BYTES`.
- Los campos de texto usan UTF-8 de ancho fijo y relleno con cero; se rechazan títulos que exceden el ancho configurado, contienen el terminador reservado o no pueden codificarse válidamente. No se truncan.
- La escritura conserva el audio byte por byte, rellena el espacio restante del slot y rechaza audio mayor que `AUDIO_SLOT_BYTES`.
- Un registro de índice apunta al inicio correcto de su grabación. Archivos con longitud no múltiplo del tamaño de registro se reportan como corruptos.
- Después de insertar una grabación, `recordings.index` queda ordenado por nombre con comparación natural alfanumérica: ignora mayúsculas y acentos; compara secuencias de dígitos por su valor numérico (por ejemplo, `Tema 2` antes de `Tema 10`); resuelve empates por nombre original y luego por `musicOffset`.
- La ordenación usa Balanced Merge Sort sobre archivos físicos auxiliares y memoria acotada a buffers/registros individuales. La búsqueda binaria del índice también lee directamente el archivo.
- Una falla al escribir el audio no publica una entrada válida en el índice; las fallas de I/O y corrupción se propagan como excepciones específicas.

## Assets

- Formato binario, big-endian para los enteros:
  - `recordings.music`: `songName[TITLE_BYTES] | audioLength[AUDIO_LENGTH_BYTES] | audioSlot[AUDIO_SLOT_BYTES]`.
  - `recordings.index`: `songName[TITLE_BYTES] | musicOffset[OFFSET_BYTES]`.
- `musicOffset` es un desplazamiento absoluto en bytes al inicio de un registro en `recordings.music`. El tamaño de registro se calcula desde las constantes; no se agrega encabezado de longitud variable.
- `Constants.java` (o equivalente) define las rutas, anchos de campos, tamaño máximo de audio, buffer de streaming y rutas de archivos temporales. Los valores no se duplican como literales en la lógica de persistencia.
- Se permiten buffers de tamaño acotado por configuración para copiar audio; no se permite materializar una grabación completa ni el índice completo en arreglos o colecciones en memoria.

## Implementation guidelines

- Implementar acceso y serialización en `src/main/java/<package>/repository/`; mantener los tipos de dominio/metadatos en `src/main/java/<package>/model/` si el proyecto ya define ese paquete.
- Separar la representación lógica `Recording` / `RecordingIndexEntry` de la codificación binaria explícita. No usar serialización nativa de Java, pues no garantiza un formato binario fijo.
- Usar `RandomAccessFile` o APIs estándar equivalentes para leer/escribir por desplazamiento; copiar el audio en streaming y validar el número de bytes real contra `audioLength`.
- Ordenar físicamente el índice con Balanced Merge Sort y archivos temporales; no sustituirlo por `Arrays.sort`, `Collections.sort` ni cargar el índice en memoria.
- Centralizar rutas y tamaños en `Constants.java` o equivalente. Manejar cada operación de disco con `try-catch` y propagar excepciones específicas, por ejemplo `IndexCorruptedException` o `RecordingNotFoundException`.
- Seguir las convenciones Java del proyecto y preferir la biblioteca estándar.

## Business workflow

1. Validar nombre, longitud y capacidad configurada antes de modificar los archivos; calcular el siguiente desplazamiento alineado a un registro fijo.
2. Escribir en `recordings.music` el nombre codificado, la longitud real y el audio desde el flujo, completando el slot con ceros sin almacenar el audio completo en memoria.
3. Solo tras completar el registro, añadir su nombre y desplazamiento a `recordings.index`.
4. Ordenar el índice en disco con Balanced Merge Sort y la comparación natural alfanumérica definida; usar temporales configurados y buffers acotados.
5. Para localizar una canción, buscar por nombre en el índice ordenado y leer desde el desplazamiento obtenido, limitando la lectura al audio de longitud declarada.
