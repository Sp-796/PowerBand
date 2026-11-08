package powerband.config;

import java.nio.file.Path;
import java.nio.file.Paths;

public final class Constants {
    public static final String RECORDINGS_FILE_PROPERTY = "powerband.recordings.file";
    public static final String INDEX_FILE_PROPERTY = "powerband.index.file";
    public static final String INDEX_TEMP_A_PROPERTY = "powerband.index.tempA";
    public static final String INDEX_TEMP_B_PROPERTY = "powerband.index.tempB";
    public static final String TITLE_BYTES_PROPERTY = "powerband.storage.titleBytes";
    public static final String MAX_COMMANDS_PROPERTY = "powerband.storage.maxCommands";
    public static final String SYNTH_ID_PROPERTY = "powerband.synth.id";
    public static final String OSC_HOST_PROPERTY = "powerband.osc.host";
    public static final String OSC_PORT_PROPERTY = "powerband.osc.port";
    public static final String OSC_NOTE_ON_ADDRESS_PROPERTY = "powerband.osc.noteOnAddress";
    public static final String OSC_NOTE_OFF_ADDRESS_PROPERTY = "powerband.osc.noteOffAddress";
    public static final String MOBILE_OSC_PORT_PROPERTY = "powerband.mobile.osc.port";
    public static final String MOBILE_OSC_NOTE_ON_ADDRESS_PROPERTY = "powerband.mobile.osc.noteOnAddress";
    public static final String MOBILE_OSC_NOTE_OFF_ADDRESS_PROPERTY = "powerband.mobile.osc.noteOffAddress";
    public static final String MIDI_DEVICE_NAME_PROPERTY = "powerband.midi.deviceName";
    public static final String GAMEPAD_NAME_PROPERTY = "powerband.gamepad.name";
    public static final String GAMEPAD_BASE_NOTE_PROPERTY = "powerband.gamepad.baseNote";
    public static final String GAMEPAD_BUTTON_NOTE_STEP_PROPERTY = "powerband.gamepad.buttonNoteStep";
    public static final String GAMEPAD_BUTTON_VELOCITY_PROPERTY = "powerband.gamepad.buttonVelocity";
    public static final String GAMEPAD_POLL_INTERVAL_PROPERTY = "powerband.gamepad.pollIntervalMillis";
    public static final String GAMEPAD_PRESSED_THRESHOLD_PROPERTY = "powerband.gamepad.pressedThreshold";

    public static final Path RECORDINGS_FILE =
            Paths.get(System.getProperty(RECORDINGS_FILE_PROPERTY, "recordings.music"));
    public static final Path INDEX_FILE =
            Paths.get(System.getProperty(INDEX_FILE_PROPERTY, "recordings.index"));
    public static final Path INDEX_TEMP_A =
            Paths.get(System.getProperty(INDEX_TEMP_A_PROPERTY, "recordings.index.tmp-a"));
    public static final Path INDEX_TEMP_B =
            Paths.get(System.getProperty(INDEX_TEMP_B_PROPERTY, "recordings.index.tmp-b"));

    public static final int TITLE_BYTES = positiveInt(TITLE_BYTES_PROPERTY, 256);
    public static final int COUNT_BYTES = Integer.BYTES;
    public static final int OFFSET_BYTES = Long.BYTES;
    public static final int TIMESTAMP_BYTES = Long.BYTES;
    public static final int SEQUENCE_BYTES = Long.BYTES;
    public static final int SYNTH_ID_BYTES = Integer.BYTES;
    public static final int COMMAND_TYPE_BYTES = Byte.BYTES;
    public static final int MIDI_NOTE_BYTES = Integer.BYTES;
    public static final int VELOCITY_BYTES = Integer.BYTES;
    public static final int COMMAND_BYTES = TIMESTAMP_BYTES + SEQUENCE_BYTES + SYNTH_ID_BYTES
            + COMMAND_TYPE_BYTES + MIDI_NOTE_BYTES + VELOCITY_BYTES;
    public static final int MAX_COMMANDS_PER_RECORD = positiveInt(MAX_COMMANDS_PROPERTY, 4096);
    public static final int COMMANDS_SLOT_BYTES = Math.multiplyExact(MAX_COMMANDS_PER_RECORD, COMMAND_BYTES);
    public static final int MUSIC_RECORD_BYTES =
            Math.addExact(Math.addExact(TITLE_BYTES, COUNT_BYTES), COMMANDS_SLOT_BYTES);
    public static final int INDEX_RECORD_BYTES = Math.addExact(TITLE_BYTES, OFFSET_BYTES);

    public static final int MIN_MIDI_VALUE = 0;
    public static final int MAX_MIDI_VALUE = 127;
    public static final int MIDI_NOTE_COUNT = MAX_MIDI_VALUE - MIN_MIDI_VALUE + 1;
    public static final int MAX_UDP_PORT = 65535;
    public static final int MIN_UDP_PORT = 1;
    public static final int NO_TRANSMITTERS = 0;
    public static final int NO_BUTTONS = 0;
    public static final int BUTTON_COUNT_INCREMENT = 1;
    public static final int LAST_BUTTON_INDEX_OFFSET = 1;
    public static final int OSC_NOTE_ON_ARGUMENT_COUNT = 2;
    public static final int OSC_NOTE_OFF_ARGUMENT_COUNT = 1;
    public static final int OSC_FIRST_ARGUMENT_INDEX = 0;
    public static final int OSC_NOTE_ON_NOTE_ARGUMENT_INDEX = OSC_FIRST_ARGUMENT_INDEX;
    public static final int OSC_NOTE_ON_VELOCITY_ARGUMENT_INDEX = OSC_FIRST_ARGUMENT_INDEX + 1;
    public static final int OSC_NOTE_OFF_NOTE_ARGUMENT_INDEX = OSC_FIRST_ARGUMENT_INDEX;
    public static final byte NOTE_OFF_CODE = 0;
    public static final byte NOTE_ON_CODE = 1;
    public static final long FIRST_SEQUENCE = 0L;
    public static final long NO_RECORDING_TIMESTAMP = -1L;
    public static final byte OSC_STRING_TERMINATOR = 0;
    public static final byte OSC_PADDING_BYTE = 0;
    public static final int DEFAULT_SYNTH_ID = configuredInt(SYNTH_ID_PROPERTY, 0);
    public static final String PLAYBACK_THREAD_NAME = "powerband-playback";
    public static final String GAMEPAD_LISTENER_THREAD_NAME = "powerband-gamepad-listener";

    public static final String DEFAULT_OSC_HOST = System.getProperty(OSC_HOST_PROPERTY, "127.0.0.1");
    public static final int DEFAULT_OSC_PORT = configuredInt(OSC_PORT_PROPERTY, 57120);
    public static final int DEFAULT_MOBILE_OSC_PORT =
            configuredInt(MOBILE_OSC_PORT_PROPERTY, 57121);
    public static final String OSC_NOTE_ON_ADDRESS =
            System.getProperty(OSC_NOTE_ON_ADDRESS_PROPERTY, "/noteOn");
    public static final String OSC_NOTE_OFF_ADDRESS =
            System.getProperty(OSC_NOTE_OFF_ADDRESS_PROPERTY, "/noteOff");
    public static final String MOBILE_OSC_NOTE_ON_ADDRESS =
            System.getProperty(MOBILE_OSC_NOTE_ON_ADDRESS_PROPERTY, "/noteOn");
    public static final String MOBILE_OSC_NOTE_OFF_ADDRESS =
            System.getProperty(MOBILE_OSC_NOTE_OFF_ADDRESS_PROPERTY, "/noteOff");
    public static final String OSC_NOTE_ON_TAGS = ",iii";
    public static final String OSC_NOTE_OFF_TAGS = ",ii";
    public static final int OSC_ALIGNMENT_BYTES = 4;
    public static final String MIDI_DEVICE_NAME = System.getProperty(MIDI_DEVICE_NAME_PROPERTY, "");
    public static final String GAMEPAD_NAME = System.getProperty(GAMEPAD_NAME_PROPERTY, "");
    public static final int GAMEPAD_BASE_NOTE =
            configuredInt(GAMEPAD_BASE_NOTE_PROPERTY, 60);
    public static final int GAMEPAD_BUTTON_NOTE_STEP =
            configuredInt(GAMEPAD_BUTTON_NOTE_STEP_PROPERTY, 1);
    public static final int GAMEPAD_BUTTON_VELOCITY =
            configuredInt(GAMEPAD_BUTTON_VELOCITY_PROPERTY, 100);
    public static final int GAMEPAD_POLL_INTERVAL_MILLIS =
            positiveInt(GAMEPAD_POLL_INTERVAL_PROPERTY, 8);
    public static final int GAMEPAD_FIRST_BUTTON_INDEX = 0;
    public static final float GAMEPAD_PRESSED_THRESHOLD =
            configuredFloat(GAMEPAD_PRESSED_THRESHOLD_PROPERTY, 0.5f);
    public static final float MIN_COMPONENT_VALUE = 0.0f;
    public static final float MAX_COMPONENT_VALUE = 1.0f;

    private Constants() {
    }

    private static int positiveInt(String property, int defaultValue) {
        int value = configuredInt(property, defaultValue);
        if (value <= 0) {
            throw new IllegalArgumentException(property + " must be positive");
        }
        return value;
    }

    private static int configuredInt(String property, int defaultValue) {
        String configuredValue = System.getProperty(property);
        if (configuredValue == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(configuredValue);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid integer for " + property, exception);
        }
    }

    private static float configuredFloat(String property, float defaultValue) {
        String configuredValue = System.getProperty(property);
        if (configuredValue == null) {
            return defaultValue;
        }
        try {
            float value = Float.parseFloat(configuredValue);
            if (Float.isNaN(value) || Float.isInfinite(value)) {
                throw new IllegalArgumentException("Invalid float for " + property);
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid float for " + property, exception);
        }
    }

    public static int gamepadNoteForButton(int buttonIndex) {
        if (buttonIndex < GAMEPAD_FIRST_BUTTON_INDEX) {
            throw new IllegalArgumentException("Gamepad button index must be non-negative");
        }
        return Math.addExact(GAMEPAD_BASE_NOTE,
                Math.multiplyExact(buttonIndex - GAMEPAD_FIRST_BUTTON_INDEX, GAMEPAD_BUTTON_NOTE_STEP));
    }
}
