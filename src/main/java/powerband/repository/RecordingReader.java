package powerband.repository;

import powerband.model.Command;

public interface RecordingReader extends AutoCloseable {
    String songName();

    int commandCount();

    Command readNext();

    @Override
    void close();
}
