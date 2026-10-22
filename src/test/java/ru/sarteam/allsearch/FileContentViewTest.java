package ru.sarteam.allsearch;

import static org.junit.jupiter.api.Assertions.*;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Spinner;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.TextExt;
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
        var missing = FileContentView.prepare(file, new SearchPattern("missing", false, true));
        var matched = FileContentView.prepare(file, new SearchPattern("second", false, true));
        onFx(() -> {
            Label numbers = new Label();
            Label highlight = new Label();
            HBox row = new HBox();
            ScrollPane scroll = new ScrollPane(row);
            row.getChildren().addAll(numbers, highlight);
            FileContentView view = new FileContentView(scroll, row, numbers, highlight);
            assertTrue(view.show(missing).isEmpty());
            view.clearHighlight();
            var hits = view.show(matched);
            view.focusHit(hits.getFirst());
            CodeArea content = (CodeArea) ((org.fxmisc.flowless.VirtualizedScrollPane<?>) scroll.getContent()).getContent();
            assertEquals(3, content.getParagraphs().size());
            assertNotNull(content.getParagraphGraphicFactory().apply(1));
            assertEquals("second", content.getSelectedText());
            Files.delete(file);
            view.setFont(FontConstants.DEFAULT_FONT_FAMILY, 20);
            assertEquals("second", content.getSelectedText());
            assertTrue(content.getStyle().contains("20px"));
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
    void allowsErrorStatusWhileSearchIsRunning() throws Exception {
        onFx(() -> {
            FXMLLoader loader = fontLoader(directory.resolve("settings.json"));
            loader.load();
            MainController controller = loader.getController();
            var start = MainController.class.getDeclaredMethod("startSearch", Path.class, SearchEngine.Options.class);
            start.setAccessible(true);
            start.invoke(controller, directory, new SearchEngine.Options("hit", false, true, List.of(), true, 1));
            Label status = (Label) loader.getNamespace().get("statusLabel");
            assertFalse(status.textProperty().isBound());
            assertDoesNotThrow(() -> status.setText("Ошибка чтения"));
            return null;
        });
    }

    @Test
    void discardsPreviewWhenSelectionIsCleared() throws Exception {
        Path file = directory.resolve("preview.txt");
        Files.writeString(file, "needle\n".repeat(10_000));
        onFx(() -> {
            FXMLLoader loader = fontLoader(directory.resolve("font.json"));
            loader.load();
            MainController controller = loader.getController();
            var show = MainController.class.getDeclaredMethod("showFileContent", Path.class);
            show.setAccessible(true);
            show.invoke(controller, file);
            var field = MainController.class.getDeclaredField("previewTask");
            field.setAccessible(true);
            var pending = (javafx.concurrent.Task<?>) field.get(controller);
            assertNotNull(pending);
            show.invoke(controller, (Object) null);
            assertTrue(pending.isCancelled());
            assertNull(field.get(controller));
            assertEquals("Файл не выбран", ((Label) loader.getNamespace().get("selectedFilePathLabel")).getText());
            return null;
        });
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

    @Test
    void rendersDarkThemeWithReadableTextAndLineNumbers() throws Exception {
        assertContentPalette("theme-dark.css", true);
    }

    @Test
    void keepsLightThemeReadable() throws Exception {
        assertContentPalette("theme-light.css", false);
    }

    @Test
    void appliesSavedThemeToSceneAndKeepsToggleLabelInSync() throws Exception {
        onFx(() -> {
            FXMLLoader loader = fontLoader(directory.resolve("theme-settings.json"));
            javafx.scene.Parent root = loader.load();
            Scene scene = new Scene(root, 1000, 700);
            scene.getStylesheets().add(MainApplication.class.getResource("search-highlight.css").toExternalForm());
            scene.getStylesheets().add(MainApplication.class.getResource("theme-light.css").toExternalForm());
            MainController controller = loader.getController();
            controller.applySavedTheme(scene);

            ToggleButton toggle = (ToggleButton) loader.getNamespace().get("themeToggle");
            boolean darkApplied = scene.getStylesheets().stream().anyMatch(s -> s.contains("theme-dark.css"));
            boolean lightApplied = scene.getStylesheets().stream().anyMatch(s -> s.contains("theme-light.css"));
            assertTrue(darkApplied ^ lightApplied, "к сцене должна быть применена ровно одна тема");
            assertEquals(darkApplied ? "Светлая тема" : "Тёмная тема", toggle.getText(),
                    "надпись на кнопке должна соответствовать применённой теме");
            assertEquals(darkApplied, toggle.isSelected());
            return null;
        });
    }

    private void assertContentPalette(String themeFile, boolean dark) throws Exception {
        Path file = directory.resolve("PaletteSample.java");
        Files.writeString(file, """
                public class Sample {
                    int number = 42;
                }
                """);
        onFx(() -> {
            ScrollPane scroll = new ScrollPane();
            FileContentView view = new FileContentView(scroll, new HBox(), new Label(), new Label());
            view.show(FileContentView.prepare(file, new SearchPattern("Sample", false, true)));
            BorderPane root = new BorderPane(scroll);
            Scene scene = new Scene(root, 600, 240);
            scene.getStylesheets().add(MainApplication.class.getResource("search-highlight.css").toExternalForm());
            scene.getStylesheets().add(MainApplication.class.getResource(themeFile).toExternalForm());
            root.applyCss();
            root.layout();

            CodeArea content = (CodeArea) ((org.fxmisc.flowless.VirtualizedScrollPane<?>) scroll.getContent()).getContent();
            List<TextExt> segments = content.lookupAll(".text").stream()
                    .filter(TextExt.class::isInstance).map(TextExt.class::cast).toList();
            assertFalse(segments.isEmpty(), "панель должна отрисовать текст файла");
            Color textFill = segments.stream()
                    .filter(text -> " {".equals(text.getText()))
                    .map(text -> (Color) text.getFill())
                    .findFirst().orElseThrow();

            Label number = (Label) content.lookupAll(".lineno").iterator().next();
            Color gutterBackground = (Color) number.getBackground().getFills().getFirst().getFill();
            Color gutterText = (Color) number.getTextFill();

            if (dark) {
                assertTrue(brightness(textFill) > 0.6, "текст в тёмной теме должен быть светлым: " + textFill);
                assertTrue(brightness(gutterBackground) < 0.3, "фон нумерации должен быть тёмным: " + gutterBackground);
                assertTrue(brightness(gutterText) > 0.4, "номера строк должны быть светлыми: " + gutterText);
            } else {
                assertTrue(brightness(textFill) < 0.3, "текст в светлой теме должен быть тёмным: " + textFill);
                assertTrue(brightness(gutterBackground) > 0.6, "фон нумерации должен быть светлым: " + gutterBackground);
            }
            return null;
        });
    }

    private static double brightness(Color color) {
        return 0.2126 * color.getRed() + 0.7152 * color.getGreen() + 0.0722 * color.getBlue();
    }

    private static <T> T onFx(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }
}
