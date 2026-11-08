package powerband.model;

import com.illposed.osc.OSCMessage;
import com.illposed.osc.OSCMessageEvent;
import com.illposed.osc.OSCMessageListener;
import com.illposed.osc.messageselector.OSCPatternAddressMessageSelector;
import com.illposed.osc.transport.OSCPortIn;
import powerband.config.Constants;

import java.io.IOException;
import java.util.List;

public final class MobileOscInstrument extends Instrument implements AutoCloseable {
    private final boolean[] heldNotes = new boolean[Constants.MIDI_NOTE_COUNT];
    private volatile HardwareInputException listenerFailure;
    private OSCPortIn receiver;

    public MobileOscInstrument(SynthCommand synthCommand) {
        super(synthCommand);
    }

    public synchronized void startListening() {
        if (receiver != null) {
            throw new HardwareInputException("Mobile OSC listener is already running");
        }
        validateConfiguration();
        OSCPortIn newReceiver = null;
        try {
            newReceiver = new OSCPortIn(Constants.DEFAULT_MOBILE_OSC_PORT);
            newReceiver.getDispatcher().addListener(
                    new OSCPatternAddressMessageSelector(Constants.MOBILE_OSC_NOTE_ON_ADDRESS),
                    this::handleNoteOn);
            newReceiver.getDispatcher().addListener(
                    new OSCPatternAddressMessageSelector(Constants.MOBILE_OSC_NOTE_OFF_ADDRESS),
                    this::handleNoteOff);
            listenerFailure = null;
            receiver = newReceiver;
            newReceiver.startListening();
        } catch (IOException | RuntimeException exception) {
            if (receiver == newReceiver) {
                receiver = null;
            }
            if (newReceiver != null) {
                try {
                    newReceiver.close();
                } catch (IOException closeException) {
                    exception.addSuppressed(closeException);
                }
            }
            throw new HardwareInputException("Could not start mobile OSC listener", exception);
        }
    }

    public synchronized void stopListening() {
        if (receiver == null) {
            return;
        }
        OSCPortIn activeReceiver = receiver;
        receiver = null;
        HardwareInputException failure = null;
        try {
            activeReceiver.close();
        } catch (IOException exception) {
            failure = new HardwareInputException("Could not close mobile OSC listener", exception);
        }
        failure = releaseHeldNotes(failure);
        if (failure != null) {
            listenerFailure = failure;
            throw failure;
        }
    }

    public HardwareInputException listenerFailure() {
        return listenerFailure;
    }

    @Override
    public void close() {
        stopListening();
    }

    private static void validateConfiguration() {
        if (Constants.DEFAULT_MOBILE_OSC_PORT < Constants.MIN_UDP_PORT
                || Constants.DEFAULT_MOBILE_OSC_PORT > Constants.MAX_UDP_PORT) {
            throw new HardwareInputException("Configured mobile OSC port is outside the UDP port range");
        }
        if (Constants.MOBILE_OSC_NOTE_ON_ADDRESS.equals(Constants.MOBILE_OSC_NOTE_OFF_ADDRESS)) {
            throw new HardwareInputException("Mobile OSC note-on and note-off addresses must be distinct");
        }
        if (!OSCMessage.isValidAddress(Constants.MOBILE_OSC_NOTE_ON_ADDRESS)
                || !OSCMessage.isValidAddress(Constants.MOBILE_OSC_NOTE_OFF_ADDRESS)) {
            throw new HardwareInputException("Configured mobile OSC address is invalid");
        }
    }

    private void handleNoteOn(OSCMessageEvent event) {
        synchronized (this) {
            try {
                List<Object> arguments = event.getMessage().getArguments();
                if (arguments.size() != Constants.OSC_NOTE_ON_ARGUMENT_COUNT) {
                    throw new HardwareInputException("Mobile OSC note-on requires MIDI note and velocity");
                }
                int midiNote = integerArgument(arguments.get(Constants.OSC_NOTE_ON_NOTE_ARGUMENT_INDEX));
                int velocity = integerArgument(arguments.get(Constants.OSC_NOTE_ON_VELOCITY_ARGUMENT_INDEX));
                noteOn(midiNote, velocity);
                heldNotes[midiNote] = true;
            } catch (RuntimeException exception) {
                listenerFailure = asHardwareFailure("Could not process mobile OSC note-on", exception);
            }
        }
    }

    private void handleNoteOff(OSCMessageEvent event) {
        synchronized (this) {
            try {
                List<Object> arguments = event.getMessage().getArguments();
                if (arguments.size() != Constants.OSC_NOTE_OFF_ARGUMENT_COUNT) {
                    throw new HardwareInputException("Mobile OSC note-off requires a MIDI note");
                }
                int midiNote = integerArgument(arguments.get(Constants.OSC_NOTE_OFF_NOTE_ARGUMENT_INDEX));
                noteOff(midiNote);
                heldNotes[midiNote] = false;
            } catch (RuntimeException exception) {
                listenerFailure = asHardwareFailure("Could not process mobile OSC note-off", exception);
            }
        }
    }

    private static int integerArgument(Object argument) {
        if (!(argument instanceof Integer)) {
            throw new HardwareInputException("Mobile OSC note arguments must be integers");
        }
        return (Integer) argument;
    }

    private static HardwareInputException asHardwareFailure(String message, RuntimeException exception) {
        return exception instanceof HardwareInputException
                ? (HardwareInputException) exception
                : new HardwareInputException(message, exception);
    }

    private HardwareInputException releaseHeldNotes(HardwareInputException failure) {
        for (int midiNote = Constants.MIN_MIDI_VALUE;
             midiNote <= Constants.MAX_MIDI_VALUE; midiNote++) {
            if (heldNotes[midiNote]) {
                try {
                    noteOff(midiNote);
                } catch (RuntimeException exception) {
                    if (failure != null) {
                        failure.addSuppressed(exception);
                    } else {
                        failure = new HardwareInputException(
                                "Could not release held mobile OSC note", exception);
                    }
                }
                heldNotes[midiNote] = false;
            }
        }
        return failure;
    }
}
