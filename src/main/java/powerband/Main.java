package powerband;

import powerband.model.GamepadInstrument;
import powerband.model.HardwareInputException;
import powerband.model.MidiKeyboardInstrument;
import powerband.model.MobileOscInstrument;
import powerband.model.SuperColliderSynthCommand;
import powerband.repository.BinaryRecordingRepository;
import powerband.service.MusicEditorService;
import powerband.service.RecordingSynthCommand;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class Main {
    public static void main(String[] args) throws IOException {
        try (SuperColliderSynthCommand synthCommand = new SuperColliderSynthCommand()) {
            BinaryRecordingRepository repository = new BinaryRecordingRepository();
            RecordingSynthCommand recordingSynth = new RecordingSynthCommand(synthCommand, repository);

            try (MusicEditorService musicEditorService =
                         new MusicEditorService(repository, recordingSynth);
                 GamepadInstrument gamepadInstrument = new GamepadInstrument(recordingSynth);
                 MidiKeyboardInstrument midiInstrument = new MidiKeyboardInstrument(recordingSynth);
                 MobileOscInstrument mobileInstrument = new MobileOscInstrument(recordingSynth);
                 BufferedReader console = new BufferedReader(new InputStreamReader(System.in))) {
                Throwable startupFailure = null;
                try {
                    gamepadInstrument.startListening();
                    midiInstrument.startListening();
                    mobileInstrument.startListening();
                    System.out.println("PowerBand listo. Presiona ENTER para salir.");
                    console.readLine();
                } catch (IOException | RuntimeException | Error failure) {
                    startupFailure = failure;
                    throw failure;
                } finally {
                    try {
                        stopListeners(gamepadInstrument, midiInstrument, mobileInstrument);
                    } catch (RuntimeException cleanupFailure) {
                        if (startupFailure != null) {
                            startupFailure.addSuppressed(cleanupFailure);
                        } else {
                            throw cleanupFailure;
                        }
                    }
                }
            }
        }
    }

    private static void stopListeners(
            GamepadInstrument gamepadInstrument,
            MidiKeyboardInstrument midiInstrument,
            MobileOscInstrument mobileInstrument) {
        HardwareInputException failure = null;
        failure = stopListener(gamepadInstrument::stopListening, failure);
        failure = stopListener(midiInstrument::stopListening, failure);
        failure = stopListener(mobileInstrument::stopListening, failure);
        if (failure != null) {
            throw failure;
        }
    }

    private static HardwareInputException stopListener(
            Runnable stopAction, HardwareInputException failure) {
        try {
            stopAction.run();
        } catch (RuntimeException exception) {
            if (failure == null) {
                failure = new HardwareInputException("Could not stop all PowerBand listeners", exception);
            } else {
                failure.addSuppressed(exception);
            }
        }
        return failure;
    }
}
