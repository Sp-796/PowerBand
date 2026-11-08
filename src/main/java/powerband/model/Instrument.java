package powerband.model;

import powerband.config.Constants;

public class Instrument {
    private final SynthCommand synthCommand;

    public Instrument(SynthCommand synthCommand) {
        if (synthCommand == null) {
            throw new IllegalArgumentException("Synth command implementation is required");
        }
        this.synthCommand = synthCommand;
    }

    public final void noteOn(int midiNote, int velocity) {
        validateMidiValue(midiNote, "MIDI note");
        validateMidiValue(velocity, "MIDI velocity");
        synthCommand.noteOn(midiNote, velocity);
    }

    public final void noteOff(int midiNote) {
        validateMidiValue(midiNote, "MIDI note");
        synthCommand.noteOff(midiNote);
    }

    public final int synthId() {
        return synthCommand.synthId();
    }

    private static void validateMidiValue(int value, String fieldName) {
        if (value < Constants.MIN_MIDI_VALUE || value > Constants.MAX_MIDI_VALUE) {
            throw new IllegalArgumentException(fieldName + " is outside the supported range");
        }
    }
}
