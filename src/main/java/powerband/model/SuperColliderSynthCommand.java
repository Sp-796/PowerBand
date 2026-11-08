package powerband.model;

import powerband.config.Constants;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

public final class SuperColliderSynthCommand implements SynthCommand, AutoCloseable {
    private final int synthId;
    private final InetAddress host;
    private final int port;
    private final DatagramSocket socket;

    public SuperColliderSynthCommand() {
        this(Constants.DEFAULT_SYNTH_ID, Constants.DEFAULT_OSC_HOST, Constants.DEFAULT_OSC_PORT);
    }

    public SuperColliderSynthCommand(int synthId, String host, int port) {
        if (synthId < 0) {
            throw new IllegalArgumentException("Synth ID must be non-negative");
        }
        if (host == null || host.isEmpty()) {
            throw new IllegalArgumentException("OSC host is required");
        }
        if (port <= 0 || port > Constants.MAX_UDP_PORT) {
            throw new IllegalArgumentException("OSC port is outside the valid range");
        }
        try {
            this.host = InetAddress.getByName(host);
            this.socket = new DatagramSocket();
        } catch (UnknownHostException | SocketException exception) {
            throw new SynthCommandException("Could not initialize SuperCollider OSC transport", exception);
        }
        this.synthId = synthId;
        this.port = port;
    }

    @Override
    public int synthId() {
        return synthId;
    }

    @Override
    public void noteOn(int midiNote, int velocity) {
        sendOscMessage(Constants.OSC_NOTE_ON_ADDRESS, Constants.OSC_NOTE_ON_TAGS,
                synthId, midiNote, velocity);
    }

    @Override
    public void noteOff(int midiNote) {
        sendOscMessage(Constants.OSC_NOTE_OFF_ADDRESS, Constants.OSC_NOTE_OFF_TAGS,
                synthId, midiNote);
    }

    private void sendOscMessage(String address, String tags, int... values) {
        if (socket.isClosed()) {
            throw new SynthCommandException("SuperCollider OSC transport is closed");
        }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream message = new DataOutputStream(bytes);
            writeOscString(message, address);
            writeOscString(message, tags);
            for (int value : values) {
                message.writeInt(value);
            }
            message.flush();
            byte[] payload = bytes.toByteArray();
            socket.send(new DatagramPacket(payload, payload.length, host, port));
        } catch (IOException exception) {
            throw new SynthCommandException("Could not send OSC command to SuperCollider", exception);
        }
    }

    private static void writeOscString(DataOutputStream target, String value) throws IOException {
        byte[] text = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        target.write(text);
        target.writeByte(Constants.OSC_STRING_TERMINATOR);
        int encodedLength = text.length + 1;
        int padding = (Constants.OSC_ALIGNMENT_BYTES
                - encodedLength % Constants.OSC_ALIGNMENT_BYTES) % Constants.OSC_ALIGNMENT_BYTES;
        for (int index = 0; index < padding; index++) {
            target.writeByte(Constants.OSC_PADDING_BYTE);
        }
    }

    @Override
    public void close() {
        socket.close();
    }
}
