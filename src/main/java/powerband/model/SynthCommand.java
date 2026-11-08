package powerband.model;

public interface SynthCommand {
    int synthId();

    void noteOn(int midiNote, int velocity);

    void noteOff(int midiNote);
}
