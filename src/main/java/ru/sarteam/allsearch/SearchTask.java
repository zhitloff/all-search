package ru.sarteam.allsearch;

import javafx.application.Platform;
import javafx.concurrent.Task;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** Фоновый поиск с потоковой выдачей файлов на потоке JavaFX. */
final class SearchTask extends Task<List<SearchEngine.Result>> {
    private final SearchEngine engine = new SearchEngine();
    private final Path root;
    private final SearchEngine.Options options;
    private final Consumer<Path> onMatchedFile;
    private volatile boolean interrupted;

    SearchTask(Path root, SearchEngine.Options options, Consumer<Path> onMatchedFile) {
        this.root = root;
        this.options = options;
        this.onMatchedFile = onMatchedFile;
    }

    @Override
    protected List<SearchEngine.Result> call() {
        Set<Path> shownFiles = new HashSet<>();
        if (interrupted) {
            return List.of();
        }
        return engine.search(root, options, file -> {
            if (interrupted) {
                engine.cancel();
            }
            updateMessage(file);
        }, file -> {
            if (shownFiles.add(file)) {
                Platform.runLater(() -> onMatchedFile.accept(file));
            }
        });
    }

    void start() {
        Thread thread = new Thread(this, "search-task");
        thread.setDaemon(true);
        thread.start();
    }

    /** Останавливает обход, сохраняя уже найденные результаты. */
    void requestStop() {
        interrupted = true;
        engine.cancel();
    }

    boolean wasInterrupted() {
        return interrupted;
    }
}
