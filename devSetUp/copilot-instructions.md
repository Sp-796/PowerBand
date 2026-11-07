# Copilot project instructions

Use Java only. Target the JDK version declared in `pom.xml` or `build.gradle`.

The entrypoint (`public static void main`) stays in `src/main/java/<package>/Main.java`.
Domain classes stay in `src/main/java/<package>/model/`.
Business logic stays in `src/main/java/<package>/service/`.
Data access stays in `src/main/java/<package>/repository/`.
Input and output types (requests, responses, DTOs) stay in `src/main/java/<package>/dto/`.
Packages are lowercase; classes are `PascalCase`; methods and fields are `camelCase`; constants are `UPPER_SNAKE_CASE`.
One public top-level type per file, and the file name matches the type name.
Depend on interfaces for services and repositories; keep fields `private` and expose behavior through methods.
Prefer the standard library. Add a dependency only when the standard library cannot do the job.

For project-specific execution flow and layer instructions, follow the repository instructions under `.github/instructions/`.

# Reglas Globales y Estrictas del Proyecto (Power Band)

- Cero valores quemados: Está absolutamente prohibido usar *hardcoding*. Todo valor, ruta de archivo, tamaño de byte o configuración de puerto debe consumirse desde una clase `Constants.java` o similar.
- Manejo de Excepciones: Toda operación de entrada/salida (I/O) en disco (`InputStream`, `OutputStream`, `RandomAccessFile`) debe usar bloques `try-catch` y lanzar excepciones personalizadas (ej. `IndexCorruptedException`, `RecordingNotFoundException`).
- Almacenamiento Físico: Prohibido cargar los índices o las grabaciones completas en arreglos en memoria (RAM). Toda búsqueda (búsqueda binaria) y ordenamiento (Balanced Merge Sort) debe ejecutarse directamente sobre los archivos físicos.
- Serialización Fija: Los archivos `recordings.music` y `recordings.index` deben construirse usando escritura binaria de tamaño exacto y fijo para permitir cálculos de desplazamiento (offsets) precisos.