package powerband.model;

public final class RecordingIndexEntry {
    private final String songName;
    private final long musicOffset;

    public RecordingIndexEntry(String songName, long musicOffset) {
        if (songName == null || songName.isEmpty()) {
            throw new IllegalArgumentException("Song name is required");
        }
        if (musicOffset < 0) {
            throw new IllegalArgumentException("Music offset must be non-negative");
        }
        this.songName = songName;
        this.musicOffset = musicOffset;
    }

    public String songName() {
        return songName;
    }

    public long musicOffset() {
        return musicOffset;
    }
}
