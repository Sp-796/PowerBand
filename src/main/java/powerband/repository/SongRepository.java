package powerband.repository;

public interface SongRepository {
    RecordingWriter beginRecording(String songName);

    RecordingReader openReader(String songName);
}
