---
applyTo: "src/main/java/**/*.java"
description: "Map of the implemented backend and the files to open before editing service or API code"
---
# Backend

The backend uses package `powerband` under `src/main/java/powerband/`.

- Bridge types live in `model/`: `Instrument` and the MIDI keyboard, mobile OSC, and gamepad abstractions delegate to `SynthCommand`.
- `SuperColliderSynthCommand` sends OSC note commands; host, port, and addresses are configured through `powerband.config.Constants`. Its default contract is `/noteOn` with integer arguments `(synthId, midiNote, velocity)` and `/noteOff` with `(synthId, midiNote)`.
- `MidiKeyboardInstrument`, `GamepadInstrument`, and `MobileOscInstrument` expose `startListening()` / `stopListening()` and implement `AutoCloseable`. Listener failures are made available through `listenerFailure()`.
- MIDI input uses `javax.sound.midi`; `powerband.midi.deviceName` selects a device by exact name, or the first available input when unset.
- JInput gamepad polling uses `powerband.gamepad.name` and the MIDI button map in `powerband.config.Constants`; buttons map by component order, starting at the configured base note and advancing by the configured step. Analog axes are ignored.
- The mobile JavaOSC listener binds the configured `powerband.mobile.osc.port` and accepts `/noteOn(midiNote, velocity)` and `/noteOff(midiNote)` integer messages; listener addresses are configurable.
- JInput 2.0.10 is the expected API. Maven Central publishes JavaOSC core 0.8 (and 0.9), but not 0.8.1; the listener implementation targets 0.8.
- `service.RecordingSynthCommand` decorates a synth implementation to record commands with monotonic timestamps. `service.MusicEditorService` reads events incrementally and schedules asynchronous playback.

Use the repository's Java conventions and service/repository boundaries when changing APIs or server-side logic. Keep recording and playback incremental through the repository interfaces.
