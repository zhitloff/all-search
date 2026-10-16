package ru.sarteam.allsearch;

import static org.junit.jupiter.api.Assertions.*;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Spinner;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import org.fxmisc.richtext.CodeArea;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

class FileContentViewTest {
    @TempDir
    Path directory;

    @BeforeAll
    static void startJavaFx() throws Exception {
        CompletableFuture<Void> started = new CompletableFuture<>();
        Platform.startup(() -> {
            Platform.setImplicitExit(false);
            started.complete(null);
        });
        started.get(10, TimeUnit.SECONDS);
    }

    @Test
    void preservesNumbersWithoutHitsAndSelectionWhenChangingFont() throws Exception {
        Path file = directory.resolve("sample.txt");
        Files.writeString(file, "first\r\nsecond\rthird");
        onFx(() -> {
            Label numbers = new Label();
            Label highlight = new Label();
            HBox row = new HBox();
            ScrollPane scroll = new ScrollPane(row);
            row.getChildren().addAll(numbers, highlight);
            FileContentView view = new FileContentView(scroll, row, numbers, highlight);
            assertTrue(view.showFile(file, new SearchPattern("missing", false, true)).isEmpty());
            view.clearHighlight();
            assertEquals("1\n2\n3", numbers.getText());
            assertEquals("", highlight.getText());
            var hits = view.showFile(file, new SearchPattern("second", false, true));
            view.focusHit(hits.getFirst());
            CodeArea content = (CodeArea) row.getChildren().getLast();
            assertEquals("second", content.getSelectedText());
            Files.delete(file);
            view.setFont("monospace", 20);
            assertEquals("second", content.getSelectedText());
            assertEquals("1\n2\n3", numbers.getText());
            assertEquals(" \n2\n ", highlight.getText());
            assertTrue(numbers.getStyle().contains("20px"));
            view.clear();
            assertEquals("", numbers.getText());
            assertEquals("", content.getText());
            return null;
        });
    }

    @Test
    void loadsMainFxmlAndLaysOutScene() throws Exception {
        onFx(() -> {
            var root = FXMLLoader.<javafx.scene.Parent>load(MainApplication.class.getResource("main-view.fxml"));
            new Scene(root, 1000, 700);
            root.applyCss();
            root.layout();
            return null;
        });
    }

    @Test
    void restoresSelectedFontAfterReloadingController() throws Exception {
        Path settingsFile = directory.resolve("settings/mask-history.json");
        onFx(() -> {
            FXMLLoader first = fontLoader(settingsFile);
            first.load();
            @SuppressWarnings("unchecked")
            ComboBox<String> families = (ComboBox<String>) first.getNamespace().get("fontFamilyCombo");
            @SuppressWarnings("unchecked")
            Spinner<Integer> size = (Spinner<Integer>) first.getNamespace().get("fontSizeSpinner");
            String chosen = families.getItems().stream()
                    .filter(family -> !family.equals(families.getValue())).findFirst().orElseThrow();
            families.setValue(chosen);
            size.getValueFactory().setValue(23);

            FXMLLoader second = fontLoader(settingsFile);
            javafx.scene.Parent root = second.load();
            assertEquals(chosen, ((ComboBox<?>) second.getNamespace().get("fontFamilyCombo")).getValue());
            assertEquals(23, ((Spinner<?>) second.getNamespace().get("fontSizeSpinner")).getValue());
            new Scene(root);
            root.applyCss();
            assertTrue(root.lookup(".content-area").getStyle().contains("23px"));
            return null;
        });
    }

    private FXMLLoader fontLoader(Path settingsFile) {
        FXMLLoader loader = new FXMLLoader(MainApplication.class.getResource("main-view.fxml"));
        loader.setControllerFactory(type -> new MainController(new FontSettingsStore(settingsFile)));
        return loader;
    }

    @Test
    void deliversMatchedFilesBeforeSearchCompletion() throws Exception {
        Path file = directory.resolve("sample.txt");
        Files.writeString(file, "needle\nneedle");
        List<Path> delivered = new ArrayList<>();
        CompletableFuture<List<SearchEngine.Result>> finished = new CompletableFuture<>();
        onFx(() -> {
            SearchTask task = new SearchTask(directory,
                    new SearchEngine.Options("needle", false, true, List.of(), true, 0), delivered::add);
            task.setOnSucceeded(event -> finished.complete(task.getValue()));
            task.setOnFailed(event -> finished.completeExceptionally(task.getException()));
            task.start();
            return null;
        });
        assertEquals(2, finished.get(10, TimeUnit.SECONDS).size());
        assertEquals(List.of(file), onFx(() -> List.copyOf(delivered)));
    }

    @Test
    void canStopBeforeStarting() throws Exception {
        CompletableFuture<Boolean> finished = new CompletableFuture<>();
        onFx(() -> {
            SearchTask task = new SearchTask(directory,
                    new SearchEngine.Options("needle", false, true, List.of(), true, 0), file -> fail());
            task.requestStop();
            task.setOnSucceeded(event -> finished.complete(task.wasInterrupted() && task.getValue().isEmpty()));
            task.setOnFailed(event -> finished.completeExceptionally(task.getException()));
            task.start();
            return null;
        });
        assertTrue(finished.get(10, TimeUnit.SECONDS));
    }

    private static <T> T onFx(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }
}
