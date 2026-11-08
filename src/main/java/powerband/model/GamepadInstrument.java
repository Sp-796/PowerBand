package powerband.model;

import net.java.games.input.Component;
import net.java.games.input.Controller;
import net.java.games.input.ControllerEnvironment;
import powerband.config.Constants;

public final class GamepadInstrument extends Instrument implements AutoCloseable {
    private volatile boolean listening;
    private volatile Thread listenerThread;
    private volatile HardwareInputException listenerFailure;
    private final ControllerEnvironment controllerEnvironment;

    public GamepadInstrument(SynthCommand synthCommand) {
        this(synthCommand, null);
    }

    public GamepadInstrument(SynthCommand synthCommand, ControllerEnvironment controllerEnvironment) {
        super(synthCommand);
        this.controllerEnvironment = controllerEnvironment;
    }

    public synchronized void startListening() {
        if (listening || listenerThread != null) {
            throw new HardwareInputException("Gamepad listener is already running");
        }
        validateMapping();
        Controller controller = findController();
        Component[] buttonComponents = findButtons(controller);
        boolean[] pressedButtons = new boolean[buttonComponents.length];
        listenerFailure = null;
        listening = true;
        listenerThread = new Thread(
                () -> pollController(controller, buttonComponents, pressedButtons),
                Constants.GAMEPAD_LISTENER_THREAD_NAME);
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    public void stopListening() {
        Thread activeThread;
        synchronized (this) {
            if (!listening && listenerThread == null) {
                return;
            }
            listening = false;
            activeThread = listenerThread;
            if (activeThread != null) {
                activeThread.interrupt();
            }
        }
        if (activeThread != null && activeThread != Thread.currentThread()) {
            try {
                activeThread.join();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new HardwareInputException("Interrupted while stopping gamepad listener", exception);
            }
        }
    }

    public HardwareInputException listenerFailure() {
        return listenerFailure;
    }

    @Override
    public void close() {
        stopListening();
    }

    private static void validateMapping() {
        if (Constants.GAMEPAD_BASE_NOTE < Constants.MIN_MIDI_VALUE
                || Constants.GAMEPAD_BASE_NOTE > Constants.MAX_MIDI_VALUE
                || Constants.GAMEPAD_BUTTON_NOTE_STEP <= Constants.MIN_MIDI_VALUE) {
            throw new HardwareInputException("Configured gamepad base MIDI note is invalid");
        }
        if (Constants.GAMEPAD_BUTTON_VELOCITY < Constants.MIN_MIDI_VALUE
                || Constants.GAMEPAD_BUTTON_VELOCITY > Constants.MAX_MIDI_VALUE) {
            throw new HardwareInputException("Configured gamepad button velocity is invalid");
        }
        if (Constants.GAMEPAD_PRESSED_THRESHOLD < Constants.MIN_COMPONENT_VALUE
                || Constants.GAMEPAD_PRESSED_THRESHOLD > Constants.MAX_COMPONENT_VALUE) {
            throw new HardwareInputException("Configured gamepad press threshold is invalid");
        }
    }

    private Controller findController() {
        Controller selectedController = null;
        ControllerEnvironment environment = controllerEnvironment == null
                ? ControllerEnvironment.getDefaultEnvironment() : controllerEnvironment;
        for (Controller controller : environment.getControllers()) {
            if (!isGamepad(controller)) {
                continue;
            }
            if (Constants.GAMEPAD_NAME.isEmpty()) {
                selectedController = controller;
                break;
            }
            if (Constants.GAMEPAD_NAME.equals(controller.getName())) {
                selectedController = controller;
                break;
            }
        }
        if (selectedController == null) {
            String requestedController = Constants.GAMEPAD_NAME.isEmpty()
                    ? "an available gamepad" : Constants.GAMEPAD_NAME;
            throw new HardwareInputException("Could not find gamepad " + requestedController);
        }
        return selectedController;
    }

    private static boolean isGamepad(Controller controller) {
        Controller.Type type = controller.getType();
        return type == Controller.Type.GAMEPAD || type == Controller.Type.STICK;
    }

    private static Component[] findButtons(Controller controller) {
        Component[] components = controller.getComponents();
        int buttonCount = Constants.NO_BUTTONS;
        for (Component component : components) {
            if (component.getIdentifier() instanceof Component.Identifier.Button) {
                buttonCount += Constants.BUTTON_COUNT_INCREMENT;
            }
        }
        if (buttonCount == Constants.NO_BUTTONS) {
            throw new HardwareInputException("Selected controller has no button components");
        }
        try {
            int lastMappedNote = Constants.gamepadNoteForButton(
                    buttonCount - Constants.LAST_BUTTON_INDEX_OFFSET);
            if (lastMappedNote < Constants.MIN_MIDI_VALUE || lastMappedNote > Constants.MAX_MIDI_VALUE) {
                throw new HardwareInputException("Configured gamepad button mapping exceeds the MIDI note range");
            }
        } catch (ArithmeticException exception) {
            throw new HardwareInputException("Configured gamepad button mapping overflows", exception);
        }
        Component[] buttons = new Component[buttonCount];
        int buttonPosition = Constants.GAMEPAD_FIRST_BUTTON_INDEX;
        for (Component component : components) {
            if (component.getIdentifier() instanceof Component.Identifier.Button) {
                buttons[buttonPosition++] = component;
            }
        }
        return buttons;
    }

    private void pollController(Controller controller, Component[] buttons, boolean[] pressedButtons) {
        HardwareInputException failure = null;
        try {
            while (listening && !Thread.currentThread().isInterrupted()) {
                if (!controller.poll()) {
                    throw new HardwareInputException("Gamepad stopped responding");
                }
                for (int buttonIndex = Constants.GAMEPAD_FIRST_BUTTON_INDEX;
                     buttonIndex < buttons.length; buttonIndex++) {
                    boolean pressed = buttons[buttonIndex].getPollData()
                            >= Constants.GAMEPAD_PRESSED_THRESHOLD;
                    if (pressed != pressedButtons[buttonIndex]) {
                        int note = Constants.gamepadNoteForButton(buttonIndex);
                        if (pressed) {
                            noteOn(note, Constants.GAMEPAD_BUTTON_VELOCITY);
                        } else {
                            noteOff(note);
                        }
                        pressedButtons[buttonIndex] = pressed;
                    }
                }
                Thread.sleep(Constants.GAMEPAD_POLL_INTERVAL_MILLIS);
            }
        } catch (InterruptedException exception) {
            if (listening) {
                Thread.currentThread().interrupt();
                failure = new HardwareInputException("Gamepad listener was interrupted unexpectedly", exception);
            }
        } catch (RuntimeException exception) {
            failure = exception instanceof HardwareInputException
                    ? (HardwareInputException) exception
                    : new HardwareInputException("Could not delegate gamepad event", exception);
        } finally {
            listening = false;
            failure = releasePressedButtons(pressedButtons, failure);
            listenerFailure = failure;
            synchronized (this) {
                listenerThread = null;
            }
        }
    }

    private HardwareInputException releasePressedButtons(
            boolean[] pressedButtons, HardwareInputException failure) {
        for (int buttonIndex = Constants.GAMEPAD_FIRST_BUTTON_INDEX;
             buttonIndex < pressedButtons.length; buttonIndex++) {
            if (pressedButtons[buttonIndex]) {
                try {
                    noteOff(Constants.gamepadNoteForButton(buttonIndex));
                } catch (RuntimeException exception) {
                    if (failure != null) {
                        failure.addSuppressed(exception);
                    } else {
                        failure = new HardwareInputException("Could not release held gamepad note", exception);
                    }
                }
                pressedButtons[buttonIndex] = false;
            }
        }
        return failure;
    }
}
