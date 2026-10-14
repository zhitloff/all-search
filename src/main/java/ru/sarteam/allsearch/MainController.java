package ru.sarteam.allsearch;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.stage.DirectoryChooser;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Контроллер главного окна. Результаты — в master-detail виде:
 * слева список файлов с совпадениями (только имена), справа — содержимое
 * выбранного файла в текстовой панели (только чтение).
 * Поиск выполняется в фоновом потоке (Task), чтобы не блокировать UI.
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
    private TextArea contentArea;
    @FXML
    private Label statusLabel;
    @FXML
    private ProgressBar progressBar;

    private static final int MAX_DISPLAY_CHARS = 2_000_000;

    private final ObservableList<Path> files = FXCollections.observableArrayList();
    private final List<SearchEngine.Result> allResults = new ArrayList<>();
    private SearchEngine engine;
    private boolean interrupted;

    @FXML
    private void initialize() {
        fileNameColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getFileName().toString()));

        fileTable.setItems(files);
        contentArea.setPromptText("Выберите файл слева, чтобы увидеть его содержимое");

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

    /** Показывает содержимое выбранного слева файла в правой панели (только чтение). */
    private void showFileContent(Path file) {
        if (file == null) {
            contentArea.clear();
            return;
        }
        try {
            String text = SearchEngine.readFileContent(file);
            if (text.length() > MAX_DISPLAY_CHARS) {
                text = text.substring(0, MAX_DISPLAY_CHARS)
                        + "\n\n… (файл обрезан для отображения: всего символов " + text.length() + ")";
            }
            contentArea.setText(text);
        } catch (IOException e) {
            contentArea.clear();
            statusLabel.setText("Не удалось прочитать файл: " + e.getMessage());
        }
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
        files.clear();
        contentArea.clear();
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
