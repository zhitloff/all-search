package ru.sarteam.allsearch;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * Главный класс JavaFX-приложения (аналог AVSearch).
 */
public class MainApplication extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(MainApplication.class.getResource("main-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 960, 620);
        scene.getStylesheets().add(MainApplication.class.getResource("search-highlight.css").toExternalForm());
        stage.setTitle("all-search — поиск по тексту в файлах");
        stage.setMinWidth(640);
        stage.setMinHeight(400);
        stage.setScene(scene);
        stage.show();
    }
}
