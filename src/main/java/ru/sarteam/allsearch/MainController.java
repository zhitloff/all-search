package ru.sarteam.allsearch;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleObjectProperty;
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
import javafx.scene.control.TextField;
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
 * Контроллер главного окна: выбор папки, параметры поиска, таблица результатов.
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
    private TableView<SearchEngine.Result> resultTable;
    @FXML
    private TableColumn<SearchEngine.Result, String> fileColumn;
    @FXML
    private TableColumn<SearchEngine.Result, Integer> lineColumn;
    @FXML
    private TableColumn<SearchEngine.Result, String> textColumn;
    @FXML
    private Label statusLabel;
    @FXML
    private ProgressBar progressBar;

    private final ObservableList<SearchEngine.Result> results = FXCollections.observableArrayList();
    private SearchEngine engine;
    private Path searchRoot;
    private boolean interrupted;

    @FXML
    private void initialize() {
        fileColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(displayPath(cell.getValue())));
        lineColumn.setCellValueFactory(cell -> new SimpleObjectProperty<>(cell.getValue().line()));
        textColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().text()));
        resultTable.setItems(results);
        resultTable.setRowFactory(tv -> {
            TableRow<SearchEngine.Result> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY
                        && event.getClickCount() == 2
                        && !row.isEmpty()) {
                    openFile(row.getItem().file());
                }
            });
            return row;
        });
        statusLabel.setText("Укажите папку и текст, затем нажмите «Искать». Двойной щелчок по строке открывает файл.");
    }

    private String displayPath(SearchEngine.Result result) {
        Path file = result.file();
        if (searchRoot != null) {
            try {
                return searchRoot.relativize(file).toString();
            } catch (IllegalArgumentException e) {
                return file.toString();
            }
        }
        return file.toString();
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
        searchRoot = root;
        results.clear();
        setRunning(true);

        engine = new SearchEngine();
        Task<List<SearchEngine.Result>> task = new Task<>() {
            @Override
            protected List<SearchEngine.Result> call() {
                return engine.search(root, options, this::updateMessage);
            }
        };
        task.setOnSucceeded(event -> {
            results.setAll(task.getValue());
            finish(interrupted
                    ? "Поиск прерван. Найдено совпадений: " + results.size()
                    : "Готово. Найдено совпадений: " + results.size());
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
