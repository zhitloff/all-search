package ru.sarteam.allsearch;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
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

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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
    private ComboBox<String> directoryField;
    @FXML
    private TextField queryField;
    @FXML
    private CheckBox regexCheck;
    @FXML
    private CheckBox caseCheck;
    @FXML
    private CheckBox textOnlyCheck;
    @FXML
    private ComboBox<String> maskField;
    @FXML
    private ComboBox<String> fontFamilyCombo;
    @FXML
    private Spinner<Integer> fontSizeSpinner;
    @FXML
    private Button searchButton;
    @FXML
    private Button prevHitButton;
    @FXML
    private Button nextHitButton;
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
    private Label lineNumberHighlightLabel;
    @FXML
    private Label statusLabel;
    @FXML
    private ProgressBar progressBar;
    @FXML
    private Label fileMatchesLabel;

    private static final int MAX_DISPLAY_CHARS = 2_000_000;
    private static final int MAX_HIGHLIGHTS = 5_000;
    private static final Path HISTORY_FILE = Path.of(
            System.getProperty("user.home"), ".all-search", "mask-history.json");
    private static final String DEFAULT_FONT_FAMILY = "monospace";
    private static final int DEFAULT_FONT_SIZE = 14;

    private final ObservableList<Path> files = FXCollections.observableArrayList();
    private final List<SearchEngine.Result> allResults = new ArrayList<>();
    private SearchEngine engine;
    private boolean interrupted;
    private String lastQuery;
    private boolean lastRegex;
    private boolean lastCaseSensitive;
    private List<String> allFontFamilies = List.of();
    private final SearchResultNavigator resultNavigator = new SearchResultNavigator();
    private final SyntaxHighlighter syntaxHighlighter = new SyntaxHighlighter();
    private final SearchOptionsFactory searchOptionsFactory = new SearchOptionsFactory();
    private LineNumberView lineNumbers;

    /** RichTextFX-область для показа содержимого файла (создаётся в {@link #initialize()}). */
    private CodeArea contentArea;
    /** Ширина контента правой панели: номера + самая длинная строка текста (px). */
    private double rowContentWidth;
    /** Количество строк в тексте, который сейчас показан справа. */
    private int currentLineCount = 1;
    private final HistoryStore historyStore = new HistoryStore(HISTORY_FILE);

    @FXML
    private void initialize() {
        fileNameColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getFileName().toString()));
        lineNumbers = new LineNumberView(lineNumberLabel, lineNumberHighlightLabel);

        fileTable.setItems(files);

        contentArea = new CodeArea();
        contentArea.setEditable(false);
        contentArea.setFocusTraversable(true);
        contentArea.getStyleClass().add("content-area");
        contentArea.setStyle("-fx-font-family: '" + DEFAULT_FONT_FAMILY + "'; -fx-font-size: " + DEFAULT_FONT_SIZE + "px;");

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

        directoryField.setEditable(true);
        maskField.setEditable(true);
        HistoryStore.History history = historyStore.load();
        directoryField.getItems().setAll(history.directories());
        maskField.getItems().setAll(history.masks());
        initFontControls();
        applyContentFont(currentFontFamily(), currentFontSize());

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

        progressBar.setProgress(0.0);
        fileMatchesLabel.setText("Совпадений в файле: 0");
        statusLabel.setText("Укажите папку и текст, затем нажмите «Искать». Двойной щелчок открывает файл.");
    }

    /** Показывает содержимое выбранного слева файла в правой панели, подсвечивая найденный текст. */
    private void showFileContent(Path file) {
        if (file == null) {
            lineNumbers.clear();
            contentArea.replaceText(0, contentArea.getLength(), "");
            resultNavigator.clear();
            updateFileMatchesLabel();
            updateHitButtons();
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
            currentLineCount = Math.max(1, lineCount);
            lineNumbers.setLineCount(lineCount);
            // Номера и текст должны иметь одинаковую высоту строк, иначе они разъедутся.
            // Высоту текстовой области задаём равной высоте колонки номеров плюс запас
            // на несколько строк, чтобы последняя строка файла не обрезалась.
            double contentHeight = lineNumbers.prefHeight();
            double lineHeight = Math.max(1, contentHeight / lineCount);
            double areaHeight = contentHeight + 3 * lineHeight;
            contentArea.setPrefHeight(areaHeight);
            contentArea.setMinHeight(areaHeight);
            contentArea.replaceText(0, contentArea.getLength(), text);
            contentArea.setStyleSpans(0, syntaxHighlighter.build(file, text, lastQuery, lastRegex, lastCaseSensitive));
            rebuildHits(text);
            updateFileMatchesLabel();
            int initialHit = resultNavigator.initialHitIndex();
            resultNavigator.clearPendingHit();
            focusHit(initialHit);
            contentArea.requestLayout();
            // Ширина ряда: не уже панели, но достаточно, чтобы поместилась самая длинная строка.
            rowContentWidth = lineNumbers.prefWidth() + widestLineWidth(text, Font.font(currentFontFamily(), currentFontSize()));
            updateRowWidth();
        } catch (IOException e) {
            lineNumbers.clear();
            contentArea.replaceText(0, contentArea.getLength(), "");
            currentLineCount = 1;
            resultNavigator.clear();
            updateFileMatchesLabel();
            updateHitButtons();
            statusLabel.setText("Не удалось прочитать файл: " + e.getMessage());
        }
    }

    private void rebuildHits(String text) {
        List<SearchResultNavigator.Hit> hits = new ArrayList<>();
        Pattern pattern = compileHighlightPattern();
        if (pattern != null) {
            Matcher matcher = pattern.matcher(text);
            while (matcher.find()) {
                int start = matcher.start();
                int end = matcher.end();
                if (end > start) {
                    hits.add(new SearchResultNavigator.Hit(start, end));
                }
            }
        }
        resultNavigator.setHits(hits);
        updateHitButtons();
    }

    private void focusHit(int index) {
        if (resultNavigator.isEmpty()) {
            lineNumbers.clear();
            updateFileMatchesLabel();
            updateHitButtons();
            return;
        }
        int safeIndex = resultNavigator.selectIndex(index);
        SearchResultNavigator.Hit hit = resultNavigator.hitAt(safeIndex);
        contentArea.requestFocus();
        contentArea.selectRange(hit.start(), hit.end());
        int line = lineFromOffsetInContent(hit.start());
        int paragraph = line - 1;
        lineNumbers.highlight(line);
        updateFileMatchesLabel();
        scrollToParagraph(paragraph);
        updateHitButtons();
    }

    /**
     * Стабильно прокручивает внешний ScrollPane к нужному параграфу (строке)
     * без многократных runLater-попыток и «дёрганья».
     */
    private void scrollToParagraph(int paragraphIndex) {
        if (textScrollPane.getContent() == null) {
            return;
        }
        double contentH = textScrollPane.getContent().getBoundsInLocal().getHeight();
        double viewportH = textScrollPane.getViewportBounds().getHeight();
        if (contentH <= viewportH || viewportH <= 0) {
            return;
        }

        int lines = Math.max(1, currentLineCount);
        int clampedParagraph = Math.max(0, Math.min(lines - 1, paragraphIndex));

        double lineHeight = Math.max(1.0, contentH / lines);
        double paragraphCenterY = (clampedParagraph + 0.5) * lineHeight;
        double targetTop = paragraphCenterY - viewportH / 2.0;

        double range = contentH - viewportH;
        targetTop = Math.max(0.0, Math.min(range, targetTop));
        textScrollPane.setVvalue(targetTop / range);
    }

    private void gotoNextHit() {
        if (resultNavigator.isEmpty()) {
            return;
        }
        SearchResultNavigator.Navigation navigation = resultNavigator.next(
                files.indexOf(fileTable.getSelectionModel().getSelectedItem()), files.size());
        if (navigation.fileDirection() == null) {
            focusHit(navigation.hitIndex());
            return;
        }
        // Дошли до конца текущего файла: переходим к первому совпадению следующего файла.
        moveToAdjacentFile(navigation.fileDirection());
    }

    private void gotoPrevHit() {
        if (resultNavigator.isEmpty()) {
            return;
        }
        SearchResultNavigator.Navigation navigation = resultNavigator.previous(
                files.indexOf(fileTable.getSelectionModel().getSelectedItem()), files.size());
        if (navigation.fileDirection() == null) {
            focusHit(navigation.hitIndex());
            return;
        }
        // Дошли до начала текущего файла: переходим к последнему совпадению предыдущего файла.
        moveToAdjacentFile(navigation.fileDirection());
    }

    /**
     * Сменить файл относительно текущего: direction=+1 (вперёд), -1 (назад).
     * Возвращает true, если смена файла действительно произошла.
     */
    private boolean moveToAdjacentFile(int direction) {
        int total = files.size();
        if (total <= 1) {
            return false;
        }
        Path currentFile = fileTable.getSelectionModel().getSelectedItem();
        int currentIndex = files.indexOf(currentFile);
        if (currentIndex < 0) {
            currentIndex = 0;
        }

        int nextIndex = (currentIndex + direction + total) % total;
        if (nextIndex == currentIndex) {
            return false;
        }

        // После смены файла ставим курсор на нужный край списка совпадений.
        resultNavigator.setPendingHitForFileDirection(direction);
        fileTable.getSelectionModel().select(nextIndex);
        fileTable.scrollTo(nextIndex);
        return true;
    }

    private void updateHitButtons() {
        boolean hasHits = !resultNavigator.isEmpty();
        prevHitButton.setDisable(!hasHits || searchButton.isDisable());
        nextHitButton.setDisable(!hasHits || searchButton.isDisable());
    }

    /**
     * RichTextFX хранит текст с переносом '\n'; если оставить CRLF, длины
     * документа и StyleSpans могут разойтись, что приводит к IOBE.
     */
    private static String normalizeLineEndings(String text) {
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }

    private void initFontControls() {
        List<String> families = Font.getFamilies().stream()
                .sorted(Comparator.naturalOrder())
                .toList();
        allFontFamilies = families;
        fontFamilyCombo.setEditable(false);
        fontFamilyCombo.getItems().setAll(families);

        if (families.contains(DEFAULT_FONT_FAMILY)) {
            fontFamilyCombo.getSelectionModel().select(DEFAULT_FONT_FAMILY);
        } else if (!families.isEmpty()) {
            fontFamilyCombo.getSelectionModel().selectFirst();
        }

        SpinnerValueFactory.IntegerSpinnerValueFactory valueFactory =
                new SpinnerValueFactory.IntegerSpinnerValueFactory(8, 72, DEFAULT_FONT_SIZE, 1);
        fontSizeSpinner.setValueFactory(valueFactory);

        fontFamilyCombo.valueProperty().addListener((obs, oldV, newV) -> {
            if (newV != null && !newV.isBlank()) {
                if (!allFontFamilies.contains(newV)) {
                    return;
                }
                applyContentFont(newV, currentFontSize());
            }
        });
        fontSizeSpinner.valueProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                applyContentFont(currentFontFamily(), newV);
            }
        });

    }

    private String currentFontFamily() {
        String family = fontFamilyCombo == null ? null : fontFamilyCombo.getValue();
        return (family == null || family.isBlank()) ? DEFAULT_FONT_FAMILY : family;
    }

    private int currentFontSize() {
        Integer size = fontSizeSpinner == null ? null : fontSizeSpinner.getValue();
        return size == null ? DEFAULT_FONT_SIZE : Math.max(8, Math.min(72, size));
    }

    private void applyContentFont(String family, int size) {
        String safeFamily = (family == null || family.isBlank()) ? DEFAULT_FONT_FAMILY : family;
        int px = Math.max(8, Math.min(72, size));
        String escaped = safeFamily.replace("'", "\\'");

        contentArea.setStyle("-fx-font-family: '" + escaped + "'; -fx-font-size: " + px + "px;");
        lineNumbers.setFont(safeFamily, px);

        Path selected = fileTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            showFileContent(selected);
        }
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
        String currentDirectory = currentDirectoryText();
        if (!currentDirectory.isBlank()) {
            try {
                Path current = Paths.get(currentDirectory);
                if (Files.isDirectory(current)) {
                    chooser.setInitialDirectory(current.toFile());
                }
            } catch (RuntimeException ignored) {
                // Некорректный путь в поле: просто не выставляем initialDirectory.
            }
        }
        File dir = chooser.showDialog(directoryField.getScene().getWindow());
        if (dir != null) {
            String selected = dir.getAbsolutePath();
            directoryField.getEditor().setText(selected);
            rememberDirectory(selected);
        }
    }

    @FXML
    private void onSearch() {
        Path root;
        String rootText = currentDirectoryText();
        try {
            root = Paths.get(rootText.trim());
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
        SearchEngine.Options options;
        try {
            options = searchOptionsFactory.create(
                    query,
                    regexCheck.isSelected(),
                    caseCheck.isSelected(),
                    currentMaskText(),
                    textOnlyCheck.isSelected());
        } catch (PatternSyntaxException e) {
            statusLabel.setText("Некорректное регулярное выражение: " + e.getDescription());
            return;
        }
        rememberDirectory(root.toString());
        rememberMask(currentMaskText());
        startSearch(root, options);
    }

    private String currentDirectoryText() {
        if (directoryField.getEditor() != null) {
            return directoryField.getEditor().getText();
        }
        return directoryField.getValue() == null ? "" : directoryField.getValue();
    }

    private String currentMaskText() {
        if (maskField.getEditor() != null) {
            return maskField.getEditor().getText();
        }
        return maskField.getValue() == null ? "" : maskField.getValue();
    }

    private void rememberMask(String rawMask) {
        String mask = rawMask == null ? "" : rawMask.trim();
        if (mask.isEmpty()) {
            return;
        }
        HistoryStore.remember(maskField.getItems(), mask, HistoryStore.MASK_LIMIT);
        maskField.setValue(mask);
        saveFieldHistories();
    }

    private void rememberDirectory(String rawDirectory) {
        String directory = rawDirectory == null ? "" : rawDirectory.trim();
        if (directory.isEmpty()) {
            return;
        }
        HistoryStore.remember(directoryField.getItems(), directory, HistoryStore.DIRECTORY_LIMIT);
        directoryField.setValue(directory);
        saveFieldHistories();
    }

    private void saveFieldHistories() {
        historyStore.save(directoryField.getItems(), maskField.getItems());
    }

    private void startSearch(Path root, SearchEngine.Options options) {
        interrupted = false;
        lastQuery = options.query();
        lastRegex = options.regex();
        lastCaseSensitive = options.caseSensitive();
        files.clear();
        lineNumbers.clear();
        contentArea.replaceText(0, contentArea.getLength(), "");
        resultNavigator.clear();
        updateFileMatchesLabel();
        allResults.clear();
        setRunning(true);

        engine = new SearchEngine();
        Set<Path> shownFiles = Collections.synchronizedSet(new HashSet<>());
        Task<List<SearchEngine.Result>> task = new Task<>() {
            @Override
            protected List<SearchEngine.Result> call() {
                return engine.search(root, options, this::updateMessage, file -> {
                    if (shownFiles.add(file)) {
                        javafx.application.Platform.runLater(() -> files.add(file));
                    }
                });
            }
        };
        task.setOnSucceeded(event -> {
            allResults.addAll(task.getValue());
            files.sort(Comparator.comparing(Path::toString, String.CASE_INSENSITIVE_ORDER));
            if (!files.isEmpty()) {
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
        progressBar.setProgress(0.0);
        setRunning(false);
        statusLabel.setText(message);
    }

    private void updateFileMatchesLabel() {
        int total = resultNavigator.hits().size();
        if (total <= 0) {
            fileMatchesLabel.setText("Совпадений в файле: 0");
            return;
        }
        int current = resultNavigator.currentHitIndex() >= 0 ? resultNavigator.currentHitIndex() + 1 : 1;
        fileMatchesLabel.setText("Совпадений в файле: " + total + " (текущее: " + current + ")");
    }

    private int lineFromOffsetInContent(int offset) {
        String text = contentArea.getText();
        int safeOffset = Math.max(0, Math.min(offset, text.length()));
        int line = 1;
        for (int i = 0; i < safeOffset; i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    @FXML
    private void onCancel() {
        interrupted = true;
        if (engine != null) {
            engine.cancel();
        }
    }

    @FXML
    private void onPrevHit() {
        gotoPrevHit();
    }

    @FXML
    private void onNextHit() {
        gotoNextHit();
    }

    private void setRunning(boolean running) {
        searchButton.setDisable(running);
        browseButton.setDisable(running);
        cancelButton.setDisable(!running);
        directoryField.setDisable(running);
        queryField.setDisable(running);
        maskField.setDisable(running);
        fontFamilyCombo.setDisable(running);
        fontSizeSpinner.setDisable(running);
        regexCheck.setDisable(running);
        caseCheck.setDisable(running);
        textOnlyCheck.setDisable(running);
        updateHitButtons();
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
