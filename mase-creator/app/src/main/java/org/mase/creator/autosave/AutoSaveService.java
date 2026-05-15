package org.mase.creator.autosave;

import org.mase.creator.model.MazeModel;
import org.mase.creator.trig.MazeTrigParser;
import org.mase.creator.trig.MazeTrigSerializer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public final class AutoSaveService {

    private final Path autoSavePath;
    private final MazeTrigParser parser;
    private final MazeTrigSerializer serializer;

    public AutoSaveService(Path autoSavePath, MazeTrigParser parser, MazeTrigSerializer serializer) {
        this.autoSavePath = autoSavePath;
        this.parser = parser;
        this.serializer = serializer;
    }

    public Path autoSavePath() {
        return autoSavePath;
    }

    public Optional<MazeModel> load() throws IOException {
        if (!Files.exists(autoSavePath)) {
            return Optional.empty();
        }
        return Optional.of(parser.parse(autoSavePath));
    }

    public void save(MazeModel model) throws IOException {
        Files.createDirectories(autoSavePath.getParent());
        Files.writeString(autoSavePath, serializer.serialize(model));
    }
}
