package ru.sarteam.allsearch;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/** Диалог выбора установленного приложения для открытия файла. */
final class ApplicationChooser {
    private ApplicationChooser() {
    }

    static void open(Path file, Window owner, Consumer<String> onError) {
        List<InstalledApplication.Entry> applications = InstalledApplication.find();
        if (applications.isEmpty()) {
            onError.accept("Установленные приложения не найдены.");
            return;
        }

        TableView<InstalledApplication.Entry> table = new TableView<>();
        javafx.collections.ObservableList<InstalledApplication.Entry> filtered =
                javafx.collections.FXCollections.observableArrayList(applications);
        table.setItems(filtered);
        table.setPrefSize(720, 420);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<InstalledApplication.Entry, String> nameColumn = new TableColumn<>("Приложение");
        nameColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().name()));
        nameColumn.setPrefWidth(220);

        TableColumn<InstalledApplication.Entry, String> pathColumn = new TableColumn<>("Путь");
        pathColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().path().toString()));
        pathColumn.setPrefWidth(500);
        table.getColumns().addAll(nameColumn, pathColumn);
        table.getSelectionModel().selectFirst();

        TextField searchField = new TextField();
        searchField.setPromptText("Поиск приложения или пути");
        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            filtered.setAll(applications.stream()
                    .filter(application -> InstalledApplication.matches(application, newValue))
                    .toList());
            table.getSelectionModel().selectFirst();
        });

        Label searchLabel = new Label("Поиск:");
        VBox content = new VBox(8, searchLabel, searchField, table);
        content.setPrefSize(720, 470);

        ButtonType openButton = new ButtonType("Открыть", ButtonBar.ButtonData.OK_DONE);
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("Открыть с помощью");
        dialog.setHeaderText("Выберите приложение для открытия файла");
        dialog.setContentText(file.getFileName().toString());
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(openButton, ButtonType.CANCEL);
        dialog.getDialogPane().lookupButton(openButton).setDisable(false);

        table.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2
                    && table.getSelectionModel().getSelectedItem() != null) {
                dialog.setResult(openButton);
                dialog.close();
            }
        });

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isEmpty() || result.get() != openButton) {
            return;
        }
        InstalledApplication.Entry selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        try {
            InstalledApplication.launch(selected, file);
        } catch (IOException | RuntimeException e) {
            onError.accept("Не удалось открыть файл выбранной программой: " + e.getMessage());
        }
    }
}
