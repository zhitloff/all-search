package ru.sarteam.allsearch;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.DirectoryChooser;

import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Контроллер главного окна. Результаты — в master-detail виде:
 * слева список файлов с совпадениями (только имена), справа — содержимое
 * выбранного файла в текстовой панели (только чтение).
 * Поиск выполняется в фоновом потоке (Task), чтобы не блокировать UI.
 *
 * <p>Правая панель построена на RichTextFX ({@link CodeArea}): в отличие от
 * JavaFX {@code TextFlow}, он поддерживает и подсветку стилей, и выделение
 * текста с копированием в системный буфер обмена.
 */
public class MainController {

    @FXML
    private TextField directoryField;
    @FXML
    private TextField queryField;
    @FXML
    private CheckBox regexCheck;
    @FXML
    private CheckBox caseCheck;
    @FXML
    private CheckBox textOnlyCheck;
    @FXML
    private TextField maskField;
    @FXML
    private Button searchButton;
    @FXML
    private Button browseButton;
    @FXML
    private Button cancelButton;
    @FXML
    private TableView<Path> fileTable;
    @FXML
    private TableColumn<Path, String> fileNameColumn;
    @FXML
    private ScrollPane textScrollPane;
    @FXML
    private HBox contentRow;
    @FXML
    private Label lineNumberLabel;
    @FXML
    private Label statusLabel;
    @FXML
    private ProgressBar progressBar;

    private static final int MAX_DISPLAY_CHARS = 2_000_000;
    private static final int MAX_HIGHLIGHTS = 5_000;

    private final ObservableList<Path> files = FXCollections.observableArrayList();
    private final List<SearchEngine.Result> allResults = new ArrayList<>();
    private SearchEngine engine;
    private boolean interrupted;
    private String lastQuery;
    private boolean lastRegex;
    private boolean lastCaseSensitive;

    /** RichTextFX-область для показа содержимого файла (создаётся в {@link #initialize()}). */
    private CodeArea contentArea;
    /** Ширина контента правой панели: номера + самая длинная строка текста (px). */
    private double rowContentWidth;

    @FXML
    private void initialize() {
        fileNameColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getFileName().toString()));

        fileTable.setItems(files);

        contentArea = new CodeArea();
        contentArea.setEditable(false);
        contentArea.setFocusTraversable(true);
        contentArea.getStyleClass().add("content-area");
        contentArea.setStyle("-fx-font-family: monospace; -fx-font-size: 12px;");

        // Номера строк и текст лежат в одном ScrollPane и прокручиваются вместе,
        // поэтому никакой синхронизации прокрутки не нужно.
        HBox.setHgrow(contentArea, Priority.ALWAYS);
        contentRow.getChildren().add(contentArea);
        // Ширина ряда подстраивается под размер панели (см. updateRowWidth).
        textScrollPane.viewportBoundsProperty().addListener((obs, oldBounds, newBounds) -> updateRowWidth());
        // На macOS CodeArea может перехватывать события тачпада.
        // Принудительно прокручиваем внешний ScrollPane, чтобы скролл работал
        // одинаково над номерами строк и над текстом.
        contentRow.addEventFilter(ScrollEvent.SCROLL, this::forwardScrollToPane);

        // Выделение и копирование текста в CodeArea.
        ContextMenu copyMenu = new ContextMenu();
        MenuItem copyItem = new MenuItem("Копировать");
        copyItem.setOnAction(event -> contentArea.copy());
        copyMenu.getItems().add(copyItem);
        contentArea.setOnContextMenuRequested(event -> {
            copyItem.setDisable(contentArea.getSelectedText().isEmpty());
            copyMenu.show(contentArea, event.getScreenX(), event.getScreenY());
        });

        fileTable.setRowFactory(tv -> {
            TableRow<Path> row = new TableRow<>();
            Tooltip tooltip = new Tooltip();
            row.itemProperty().addListener((obs, oldItem, newItem) -> {
                if (newItem == null) {
                    Tooltip.uninstall(row, tooltip);
                } else {
                    tooltip.setText(newItem.toString());
                    Tooltip.install(row, tooltip);
                }
            });
            row.setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2 && !row.isEmpty()) {
                    openFile(row.getItem());
                }
            });
            return row;
        });

        fileTable.getSelectionModel().selectedItemProperty().addListener((obs, oldFile, newFile) -> showFileContent(newFile));

        statusLabel.setText("Укажите папку и текст, затем нажмите «Искать». Двойной щелчок открывает файл.");
    }

    /** Показывает содержимое выбранного слева файла в правой панели, подсвечивая найденный текст. */
    private void showFileContent(Path file) {
        if (file == null) {
            lineNumberLabel.setText("");
            contentArea.replaceText(0, contentArea.getLength(), "");
            return;
        }
        try {
            String text = normalizeLineEndings(SearchEngine.readFileContent(file));
            if (text.length() > MAX_DISPLAY_CHARS) {
                int fullLength = text.length();
                text = text.substring(0, MAX_DISPLAY_CHARS)
                        + "\n\n… (файл обрезан для отображения: всего символов " + fullLength + ")";
            }
            int lineCount = countLines(text);
            lineNumberLabel.setText(buildLineNumbers(lineCount));
            // Номера и текст должны иметь одинаковую высоту строк, иначе они разъедутся.
            // Высоту текстовой области задаём равной высоте колонки номеров плюс запас
            // на несколько строк, чтобы последняя строка файла не обрезалась.
            double contentHeight = lineNumberLabel.prefHeight(-1);
            double lineHeight = Math.max(1, contentHeight / lineCount);
            double areaHeight = contentHeight + 3 * lineHeight;
            contentArea.setPrefHeight(areaHeight);
            contentArea.setMinHeight(areaHeight);
            contentArea.replaceText(0, contentArea.getLength(), text);
            contentArea.setStyleSpans(0, buildHighlightSpans(text));
            contentArea.requestLayout();
            // Ширина ряда: не уже панели, но достаточно, чтобы поместилась самая длинная строка.
            rowContentWidth = lineNumberLabel.prefWidth(-1) + widestLineWidth(text, Font.font("monospace", 12));
            updateRowWidth();
        } catch (IOException e) {
            lineNumberLabel.setText("");
            contentArea.replaceText(0, contentArea.getLength(), "");
            statusLabel.setText("Не удалось прочитать файл: " + e.getMessage());
        }
    }

    /**
     * RichTextFX хранит текст с переносом '\n'; если оставить CRLF, длины
     * документа и StyleSpans могут разойтись, что приводит к IOBE.
     */
    private static String normalizeLineEndings(String text) {
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }

    /** Число строк в тексте (считает символы '\n'). */
    private static int countLines(String text) {
        int count = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                count++;
            }
        }
        return count;
    }

    /** Номера строк от 1 до {@code count}, по одному на строку. */
    private static String buildLineNumbers(int count) {
        StringBuilder sb = new StringBuilder(count * 4);
        for (int i = 1; i <= count; i++) {
            if (i > 1) {
                sb.append('\n');
            }
            sb.append(i);
        }
        return sb.toString();
    }

    /**
     * Ширина ряда с номерами и текстом: не уже видимой панели, но достаточно,
     * чтобы поместилась самая длинная строка файла (тогда появится горизонтальная
     * прокрутка ScrollPane).
     */
    private void updateRowWidth() {
        double viewportWidth = textScrollPane.getViewportBounds().getWidth();
        double rowWidth = Math.max(viewportWidth, rowContentWidth);
        contentRow.setPrefWidth(rowWidth);
        contentRow.setMinWidth(rowWidth);
    }

    /**
     * Пробрасывает скролл тачпада/колеса в ScrollPane.
     * Нужно из-за того, что RichTextFX CodeArea может съедать ScrollEvent.
     */
    private void forwardScrollToPane(ScrollEvent event) {
        if (textScrollPane.getContent() == null) {
            return;
        }

        double contentH = textScrollPane.getContent().getBoundsInLocal().getHeight();
        double viewportH = textScrollPane.getViewportBounds().getHeight();
        double contentW = textScrollPane.getContent().getBoundsInLocal().getWidth();
        double viewportW = textScrollPane.getViewportBounds().getWidth();

        boolean moved = false;

        if (contentH > viewportH && event.getDeltaY() != 0) {
            double pixelRange = Math.max(1.0, contentH - viewportH);
            double delta = -event.getDeltaY() / pixelRange;
            textScrollPane.setVvalue(clamp01(textScrollPane.getVvalue() + delta));
            moved = true;
        }

        if (contentW > viewportW && event.getDeltaX() != 0) {
            double pixelRange = Math.max(1.0, contentW - viewportW);
            double delta = -event.getDeltaX() / pixelRange;
            textScrollPane.setHvalue(clamp01(textScrollPane.getHvalue() + delta));
            moved = true;
        }

        if (moved) {
            event.consume();
        }
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    /** Ширина самой длинной строки в пикселях (для расчёта ширины ряда). */
    private static double widestLineWidth(String text, Font font) {
        Text probe = new Text("0");
        probe.setFont(font);
        double maxWidth = 0;
        int start = 0;
        for (int i = 0; i <= text.length(); i++) {
            if (i == text.length() || text.charAt(i) == '\n') {
                probe.setText(text.substring(start, i));
                double w = probe.getBoundsInLocal().getWidth();
                if (w > maxWidth) {
                    maxWidth = w;
                }
                start = i + 1;
            }
        }
        return maxWidth;
    }

    /**
     * Стилевые интервалы для подсветки совпадений с последним поисковым
     * запросом (в т.ч. по рег. выражению и с учётом регистра).
     */
    private StyleSpans<Collection<String>> buildHighlightSpans(String text) {
        StyleSpansBuilder<Collection<String>> builder = new StyleSpansBuilder<>();
        Pattern pattern = compileHighlightPattern();
        int pos = 0;
        if (pattern != null) {
            Matcher matcher = pattern.matcher(text);
            int count = 0;
            while (matcher.find()) {
                int start = matcher.start();
                int end = matcher.end();
                if (end == start) {
                    continue; // пустые совпадения не подсвечиваем
                }
                if (start > pos) {
                    builder.add(Collections.emptyList(), start - pos);
                }
                builder.add(Collections.singleton("search-hit"), end - start);
                pos = end;
                if (++count >= MAX_HIGHLIGHTS) {
                    break; // предохранитель от огромного числа фрагментов
                }
            }
        }
        if (pos < text.length()) {
            builder.add(Collections.emptyList(), text.length() - pos);
        }
        return builder.create();
    }

    /** Паттерн для подсветки — те же правила, что у движка поиска. */
    private Pattern compileHighlightPattern() {
        if (lastQuery == null || lastQuery.isEmpty()) {
            return null;
        }
        int flags = lastCaseSensitive ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
        String expr = lastRegex ? lastQuery : Pattern.quote(lastQuery);
        return Pattern.compile(expr, flags);
    }

    @FXML
    private void onBrowse() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Выберите папку для поиска");
        if (!directoryField.getText().isBlank()) {
            Path current = Paths.get(directoryField.getText());
            if (Files.isDirectory(current)) {
                chooser.setInitialDirectory(current.toFile());
            }
        }
        File dir = chooser.showDialog(directoryField.getScene().getWindow());
        if (dir != null) {
            directoryField.setText(dir.getAbsolutePath());
        }
    }

    @FXML
    private void onSearch() {
        Path root;
        try {
            root = Paths.get(directoryField.getText().trim());
        } catch (RuntimeException e) {
            statusLabel.setText("Указан некорректный путь к папке.");
            return;
        }
        if (!Files.isDirectory(root)) {
            statusLabel.setText("Папка не найдена: " + root);
            return;
        }
        String query = queryField.getText();
        if (query.isBlank()) {
            statusLabel.setText("Введите текст для поиска.");
            return;
        }
        if (regexCheck.isSelected()) {
            try {
                Pattern.compile(query);
            } catch (PatternSyntaxException e) {
                statusLabel.setText("Некорректное регулярное выражение: " + e.getDescription());
                return;
            }
        }
        SearchEngine.Options options = new SearchEngine.Options(
                query,
                regexCheck.isSelected(),
                caseCheck.isSelected(),
                parseMasks(maskField.getText()),
                textOnlyCheck.isSelected(),
                0);
        startSearch(root, options);
    }

    private static List<String> parseMasks(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> masks = new ArrayList<>();
        for (String part : text.split("[;,]\\s*|\\s+")) {
            if (!part.isBlank()) {
                masks.add(part);
            }
        }
        return masks;
    }

    private void startSearch(Path root, SearchEngine.Options options) {
        interrupted = false;
        lastQuery = options.query();
        lastRegex = options.regex();
        lastCaseSensitive = options.caseSensitive();
        files.clear();
        lineNumberLabel.setText("");
        contentArea.replaceText(0, contentArea.getLength(), "");
        allResults.clear();
        setRunning(true);

        engine = new SearchEngine();
        Task<List<SearchEngine.Result>> task = new Task<>() {
            @Override
            protected List<SearchEngine.Result> call() {
                return engine.search(root, options, this::updateMessage);
            }
        };
        task.setOnSucceeded(event -> {
            allResults.addAll(task.getValue());
            List<Path> distinctFiles = allResults.stream()
                    .map(SearchEngine.Result::file)
                    .distinct()
                    .toList();
            files.setAll(distinctFiles);
            if (!distinctFiles.isEmpty()) {
                fileTable.getSelectionModel().select(0); // заполнит правую панель содержимым
            }
            finish(interrupted
                    ? "Поиск прерван. Файлов: " + files.size() + ", совпадений: " + allResults.size()
                    : "Готово. Файлов: " + files.size() + ", совпадений: " + allResults.size());
        });
        task.setOnFailed(event -> {
            Throwable err = task.getException();
            finish("Ошибка поиска: " + (err == null ? "неизвестная" : String.valueOf(err.getMessage())));
            if (err != null) {
                err.printStackTrace();
            }
        });
        task.setOnCancelled(event -> finish("Поиск отменён."));
        progressBar.progressProperty().bind(task.progressProperty());
        statusLabel.textProperty().bind(task.messageProperty());
        new Thread(task, "search-task").start();
    }

    private void finish(String message) {
        statusLabel.textProperty().unbind();
        progressBar.progressProperty().unbind();
        progressBar.setProgress(1.0);
        setRunning(false);
        statusLabel.setText(message);
    }

    @FXML
    private void onCancel() {
        interrupted = true;
        if (engine != null) {
            engine.cancel();
        }
    }

    private void setRunning(boolean running) {
        searchButton.setDisable(running);
        browseButton.setDisable(running);
        cancelButton.setDisable(!running);
        directoryField.setDisable(running);
        queryField.setDisable(running);
        maskField.setDisable(running);
        regexCheck.setDisable(running);
        caseCheck.setDisable(running);
        textOnlyCheck.setDisable(running);
    }

    private void openFile(Path file) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(file.toFile());
            } else {
                statusLabel.setText("Открытие файлов не поддерживается на этой системе.");
            }
        } catch (IOException e) {
            statusLabel.setText("Не удалось открыть файл: " + e.getMessage());
        }
    }
}
