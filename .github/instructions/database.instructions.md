---
applyTo: "src/main/java/**/*.java,src/test/java/**/*.java"
description: "Map of the implemented database and the files to open before editing persistence code"
---
# Database

Persistent recording data lives in `src/main/java/powerband/repository/`; `powerband.config.Constants` defines the binary layout and configured paths. `BinaryRecordingRepository` implements the `SongRepository` port.

- `recordings.music`: fixed record of UTF-8 title, command count, and a fixed-capacity command slot. Commands use big-endian timestamp, sequence, synth ID, type, MIDI note, and velocity fields.
- `recordings.index`: fixed UTF-8 title plus absolute music offset. It is searched with on-disk binary search and sorted with two-file Balanced Merge Sort.
- `RecordingWriter` and `RecordingReader` stream individual commands; do not materialize complete recordings or index files.
- File paths, title width, command capacity, and temporary sort paths are configurable through the `powerband.*` system properties in `Constants`.

Open only persistence files tied to the behavior being changed. Preserve bounded-memory streaming, fixed-width binary encoding, and explicit I/O exceptions.
