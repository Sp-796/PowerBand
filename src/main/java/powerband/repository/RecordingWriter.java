package powerband.repository;

import powerband.model.Command;

public interface RecordingWriter extends AutoCloseable {
    void append(Command command);

    @Override
    void close();
}
