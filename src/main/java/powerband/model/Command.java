package powerband.model;

import powerband.config.Constants;

public final class Command {
    public enum Type {
        NOTE_OFF(Constants.NOTE_OFF_CODE),
        NOTE_ON(Constants.NOTE_ON_CODE);

        private final byte code;

        Type(byte code) {
            this.code = code;
        }

        public byte code() {
            return code;
        }

        public static Type fromCode(byte code) {
            for (Type type : values()) {
                if (type.code == code) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Unsupported command type: " + code);
        }
    }

    private final long elapsedNanos;
    private final long sequence;
    private final int synthId;
    private final Type type;
    private final int midiNote;
    private final int velocity;

    public Command(long elapsedNanos, long sequence, int synthId, Type type, int midiNote, int velocity) {
        if (elapsedNanos < 0 || sequence < 0 || synthId < 0) {
            throw new IllegalArgumentException("Command timing, sequence, and synth ID must be non-negative");
        }
        if (type == null) {
            throw new IllegalArgumentException("Command type is required");
        }
        if (midiNote < Constants.MIN_MIDI_VALUE || midiNote > Constants.MAX_MIDI_VALUE) {
            throw new IllegalArgumentException("MIDI note is outside the supported range");
        }
        if (velocity < Constants.MIN_MIDI_VALUE || velocity > Constants.MAX_MIDI_VALUE) {
            throw new IllegalArgumentException("MIDI velocity is outside the supported range");
        }
        this.elapsedNanos = elapsedNanos;
        this.sequence = sequence;
        this.synthId = synthId;
        this.type = type;
        this.midiNote = midiNote;
        this.velocity = velocity;
    }

    public long elapsedNanos() {
        return elapsedNanos;
    }

    public long sequence() {
        return sequence;
    }

    public int synthId() {
        return synthId;
    }

    public Type type() {
        return type;
    }

    public int midiNote() {
        return midiNote;
    }

    public int velocity() {
        return velocity;
    }
}
