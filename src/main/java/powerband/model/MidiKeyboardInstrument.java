package powerband.model;

import powerband.config.Constants;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import javax.sound.midi.Sequencer;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Synthesizer;
import javax.sound.midi.Transmitter;

public final class MidiKeyboardInstrument extends Instrument implements AutoCloseable {
    private MidiDevice midiDevice;
    private Transmitter transmitter;
    private MidiReceiver midiReceiver;
    private volatile HardwareInputException listenerFailure;
    private final MidiDevice configuredDevice;

    public MidiKeyboardInstrument(SynthCommand synthCommand) {
        this(synthCommand, null);
    }

    public MidiKeyboardInstrument(SynthCommand synthCommand, MidiDevice configuredDevice) {
        super(synthCommand);
        this.configuredDevice = configuredDevice;
    }

    public synchronized void startListening() {
        if (transmitter != null) {
            throw new HardwareInputException("MIDI keyboard listener is already running");
        }
        listenerFailure = null;

        MidiDevice selectedDevice = null;
        Transmitter selectedTransmitter = null;
        MidiReceiver selectedReceiver = null;
        try {
            selectedDevice = configuredDevice == null ? findInputDevice() : configuredDevice;
            if (selectedDevice.getMaxTransmitters() == Constants.NO_TRANSMITTERS) {
                throw new HardwareInputException("Configured MIDI device does not provide an input transmitter");
            }
            selectedDevice.open();
            selectedTransmitter = selectedDevice.getTransmitter();
            selectedReceiver = new MidiReceiver();
            selectedTransmitter.setReceiver(selectedReceiver);
            midiDevice = selectedDevice;
            transmitter = selectedTransmitter;
            midiReceiver = selectedReceiver;
        } catch (MidiUnavailableException | RuntimeException exception) {
            closeAfterFailure(selectedReceiver, selectedTransmitter, selectedDevice);
            throw new HardwareInputException("Could not start MIDI keyboard listener", exception);
        }
    }

    public synchronized void stopListening() {
        if (transmitter == null) {
            return;
        }
        MidiReceiver receiver = midiReceiver;
        Transmitter activeTransmitter = transmitter;
        MidiDevice activeDevice = midiDevice;
        midiReceiver = null;
        transmitter = null;
        midiDevice = null;
        receiver.releaseHeldNotes();
        receiver.close();
        activeTransmitter.close();
        activeDevice.close();
    }

    public HardwareInputException listenerFailure() {
        return listenerFailure;
    }

    @Override
    public void close() {
        stopListening();
    }

    private MidiDevice findInputDevice() throws MidiUnavailableException {
        MidiDevice.Info[] devices = MidiSystem.getMidiDeviceInfo();
        MidiDevice.Info selectedInfo = null;
        for (MidiDevice.Info info : devices) {
            boolean nameMatches = Constants.MIDI_DEVICE_NAME.isEmpty()
                    || Constants.MIDI_DEVICE_NAME.equals(info.getName());
            if (!nameMatches) {
                continue;
            }
            MidiDevice device = MidiSystem.getMidiDevice(info);
            if (!(device instanceof Sequencer)
                    && !(device instanceof Synthesizer)
                    && device.getMaxTransmitters() != Constants.NO_TRANSMITTERS) {
                selectedInfo = info;
                break;
            }
        }
        if (selectedInfo == null) {
            String requestedDevice = Constants.MIDI_DEVICE_NAME.isEmpty()
                    ? "an available MIDI input" : Constants.MIDI_DEVICE_NAME;
            throw new HardwareInputException("Could not find MIDI input device " + requestedDevice);
        }
        return MidiSystem.getMidiDevice(selectedInfo);
    }

    private static void closeAfterFailure(Receiver receiver, Transmitter transmitter, MidiDevice device) {
        if (receiver != null) {
            receiver.close();
        }
        if (transmitter != null) {
            transmitter.close();
        }
        if (device != null && device.isOpen()) {
            device.close();
        }
    }

    private final class MidiReceiver implements Receiver {
        private final boolean[] heldNotes = new boolean[Constants.MIDI_NOTE_COUNT];
        private boolean closed;

        @Override
        public synchronized void send(MidiMessage message, long timeStamp) {
            if (closed || !(message instanceof ShortMessage)) {
                return;
            }
            ShortMessage shortMessage = (ShortMessage) message;
            int command = shortMessage.getCommand();
            int note = shortMessage.getData1();
            int velocity = shortMessage.getData2();
            try {
                if (command == ShortMessage.NOTE_ON && velocity > Constants.MIN_MIDI_VALUE) {
                    MidiKeyboardInstrument.this.noteOn(note, velocity);
                    heldNotes[note] = true;
                } else if (command == ShortMessage.NOTE_OFF
                        || (command == ShortMessage.NOTE_ON && velocity == Constants.MIN_MIDI_VALUE)) {
                    MidiKeyboardInstrument.this.noteOff(note);
                    heldNotes[note] = false;
                }
            } catch (RuntimeException exception) {
                MidiKeyboardInstrument.this.listenerFailure = new HardwareInputException(
                        "Could not delegate MIDI keyboard event", exception);
            }
        }

        private synchronized void releaseHeldNotes() {
            for (int note = Constants.MIN_MIDI_VALUE; note <= Constants.MAX_MIDI_VALUE; note++) {
                if (heldNotes[note]) {
                    try {
                        MidiKeyboardInstrument.this.noteOff(note);
                    } catch (RuntimeException exception) {
                        MidiKeyboardInstrument.this.listenerFailure = new HardwareInputException(
                                "Could not release held MIDI note", exception);
                    }
                    heldNotes[note] = false;
                }
            }
        }

        @Override
        public synchronized void close() {
            closed = true;
        }
    }
}
