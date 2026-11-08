# Copilot project instructions

Use Java only. Target the JDK version declared in `pom.xml` or `build.gradle`.
This repository currently has no Maven or Gradle build descriptor; the source is kept compatible with the Java 8 API until a build target is declared.

The entrypoint (`public static void main`) stays in `src/main/java/<package>/Main.java`.
Domain classes stay in `src/main/java/<package>/model/`.
Business logic stays in `src/main/java/<package>/service/`.
Data access stays in `src/main/java/<package>/repository/`.
Input and output types (requests, responses, DTOs) stay in `src/main/java/<package>/dto/`.
Packages are lowercase; classes are `PascalCase`; methods and fields are `camelCase`; constants are `UPPER_SNAKE_CASE`.
One public top-level type per file.
Depend on interfaces for services and repositories; keep fields `private` and expose behavior through methods.
Prefer the standard library. Add a dependency only when the standard library cannot do the job.

For project-specific execution flow and layer instructions, follow the repository instructions under `.github/instructions/`.

## Power Band requirements

- Centralize file paths, field widths, slot sizes, limits, and hardware/network settings in `Constants.java` or an equivalent configuration class.
- Wrap disk I/O failures in explicit domain exceptions; do not report success after a failed operation.
- Do not load complete recording or index files into memory. Search and sorting must operate on physical files with bounded memory.
- Persist `recordings.music` and `recordings.index` using fixed-size binary records so offsets remain calculable.

The current Java package root is `powerband`. Binary recording persistence is implemented by `repository.BinaryRecordingRepository`; instrument abstractions and the SuperCollider OSC sender are in `model/`; recording capture and asynchronous playback are in `service/`.
