package ru.sarteam.allsearch;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
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
import javafx.scene.layout.HBox;
import javafx.scene.text.Font;
import javafx.stage.DirectoryChooser;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.regex.PatternSyntaxException;

/**
 * Контроллер главного окна. Результаты — в master-detail виде:
 * слева список файлов с совпадениями (только имена), справа — содержимое
 * выбранного файла в текстовой панели (только чтение).
 * Поиск выполняется в фоновом потоке (Task), чтобы не блокировать UI.
 *
 * <p>Показ текста и управление прокруткой делегируются {@link FileContentView}.
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

    private static final Path HISTORY_FILE = Path.of(
            System.getProperty("user.home"), ".all-search", "mask-history.json");
    private final ObservableList<Path> files = FXCollections.observableArrayList();
    private SearchTask searchTask;
    private SearchPattern searchPattern = new SearchPattern("", false, false);
    private List<String> allFontFamilies = List.of();
    private final SearchResultNavigator resultNavigator = new SearchResultNavigator();
    private final SearchOptionsFactory searchOptionsFactory = new SearchOptionsFactory();
    private FileContentView contentView;
    private final HistoryStore historyStore = new HistoryStore(HISTORY_FILE);
    private final FontSettingsStore fontSettingsStore;

    public MainController() {
        this(new FontSettingsStore(HISTORY_FILE));
    }

    MainController(FontSettingsStore fontSettingsStore) {
        this.fontSettingsStore = fontSettingsStore;
    }

    @FXML
    private void initialize() {
        fileNameColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getFileName().toString()));
        contentView = new FileContentView(textScrollPane, contentRow, lineNumberLabel, lineNumberHighlightLabel);

        fileTable.setItems(files);

        directoryField.setEditable(true);
        maskField.setEditable(true);
        HistoryStore.History history = historyStore.load();
        directoryField.getItems().setAll(history.directories());
        maskField.getItems().setAll(history.masks());
        initFontControls();
        applyContentFont(currentFontFamily(), currentFontSize());

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
            row.setOnContextMenuRequested(event -> {
                if (!row.isEmpty()) {
                    fileTable.getSelectionModel().select(row.getIndex());
                    createFileContextMenu(row.getItem()).show(row, event.getScreenX(), event.getScreenY());
                    event.consume();
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
            contentView.clear();
            resultNavigator.clear();
            updateFileMatchesLabel();
            updateHitButtons();
            return;
        }
        try {
            resultNavigator.setHits(contentView.showFile(file, searchPattern));
            updateFileMatchesLabel();
            int initialHit = resultNavigator.initialHitIndex();
            resultNavigator.clearPendingHit();
            focusHit(initialHit);
        } catch (IOException e) {
            contentView.clear();
            resultNavigator.clear();
            updateFileMatchesLabel();
            updateHitButtons();
            statusLabel.setText("Не удалось прочитать файл: " + e.getMessage());
        }
    }

    private void focusHit(int index) {
        if (resultNavigator.isEmpty()) {
            contentView.clearHighlight();
            updateFileMatchesLabel();
            updateHitButtons();
            return;
        }
        int safeIndex = resultNavigator.selectIndex(index);
        contentView.focusHit(resultNavigator.hitAt(safeIndex));
        updateFileMatchesLabel();
        updateHitButtons();
    }

    private void gotoNextHit() {
        if (resultNavigator.isEmpty()) {
            return;
        }
        SearchResultNavigator.Navigation navigation = resultNavigator.next(
                files.indexOf(fileTable.getSelectionModel().getSelectedItem()), files.size());
        if (navigation.fileIndex() == null) {
            focusHit(navigation.hitIndex());
            return;
        }
        // Дошли до конца текущего файла: переходим к первому совпадению следующего файла.
        selectFile(navigation.fileIndex());
    }

    private void gotoPrevHit() {
        if (resultNavigator.isEmpty()) {
            return;
        }
        SearchResultNavigator.Navigation navigation = resultNavigator.previous(
                files.indexOf(fileTable.getSelectionModel().getSelectedItem()), files.size());
        if (navigation.fileIndex() == null) {
            focusHit(navigation.hitIndex());
            return;
        }
        // Дошли до начала текущего файла: переходим к последнему совпадению предыдущего файла.
        selectFile(navigation.fileIndex());
    }

    private void selectFile(int index) {
        fileTable.getSelectionModel().select(index);
        fileTable.scrollTo(index);
    }

    private void updateHitButtons() {
        boolean hasHits = !resultNavigator.isEmpty();
        prevHitButton.setDisable(!hasHits || searchButton.isDisable());
        nextHitButton.setDisable(!hasHits || searchButton.isDisable());
    }

    private void initFontControls() {
        FontSettingsStore.Settings settings = fontSettingsStore.load();
        List<String> families = Font.getFamilies().stream()
                .sorted(Comparator.naturalOrder())
                .toList();
        allFontFamilies = families;
        fontFamilyCombo.setEditable(false);
        fontFamilyCombo.getItems().setAll(families);

        if (families.contains(settings.family())) {
            fontFamilyCombo.getSelectionModel().select(settings.family());
        } else if (families.contains(FontConstants.DEFAULT_FONT_FAMILY)) {
            fontFamilyCombo.getSelectionModel().select(FontConstants.DEFAULT_FONT_FAMILY);
        } else if (!families.isEmpty()) {
            fontFamilyCombo.getSelectionModel().selectFirst();
        }

        SpinnerValueFactory.IntegerSpinnerValueFactory valueFactory =
                new SpinnerValueFactory.IntegerSpinnerValueFactory(8, 72, settings.size(), 1);
        fontSizeSpinner.setValueFactory(valueFactory);

        fontFamilyCombo.valueProperty().addListener((obs, oldV, newV) -> {
            if (newV != null && !newV.isBlank()) {
                if (!allFontFamilies.contains(newV)) {
                    return;
                }
                applyContentFont(newV, currentFontSize());
                fontSettingsStore.save(newV, currentFontSize());
            }
        });
        fontSizeSpinner.valueProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                applyContentFont(currentFontFamily(), newV);
                fontSettingsStore.save(currentFontFamily(), newV);
            }
        });

    }

    private String currentFontFamily() {
        String family = fontFamilyCombo == null ? null : fontFamilyCombo.getValue();
        return (family == null || family.isBlank()) ? FontConstants.DEFAULT_FONT_FAMILY : family;
    }

    private int currentFontSize() {
        Integer size = fontSizeSpinner == null ? null : fontSizeSpinner.getValue();
        return size == null ? FontConstants.DEFAULT_FONT_SIZE : Math.max(8, Math.min(72, size));
    }

    private void applyContentFont(String family, int size) {
        contentView.setFont(family, size);
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
        searchPattern = new SearchPattern(options.query(), options.regex(), options.caseSensitive());
        files.clear();
        contentView.clear();
        resultNavigator.clear();
        updateFileMatchesLabel();
        setRunning(true);

        SearchTask task = new SearchTask(root, options, files::add);
        searchTask = task;
        task.setOnSucceeded(event -> {
            files.sort(Comparator.comparing(Path::toString, String.CASE_INSENSITIVE_ORDER));
            if (!files.isEmpty()) {
                fileTable.getSelectionModel().select(0); // заполнит правую панель содержимым
            }
            finish((task.wasInterrupted() ? "Поиск прерван." : "Готово.")
                    + " Файлов: " + files.size() + ", совпадений: " + task.getValue().size());
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
        task.start();
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

    @FXML
    private void onCancel() {
        if (searchTask != null) {
            searchTask.requestStop();
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

    private ContextMenu createFileContextMenu(Path file) {
        MenuItem open = new MenuItem("Открыть");
        open.setOnAction(event -> openWithDefaultApplication(file));

        MenuItem openWith = new MenuItem("Открыть с помощью...");
        openWith.setOnAction(event -> chooseApplicationAndOpen(file));

        return new ContextMenu(open, openWith);
    }

    private void openWithDefaultApplication(Path file) {
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

    private void chooseApplicationAndOpen(Path file) {
        ApplicationChooser.open(file, fileTable.getScene().getWindow(), statusLabel::setText);
    }
}
