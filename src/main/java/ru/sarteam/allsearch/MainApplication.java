package ru.sarteam.allsearch;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * Главный класс JavaFX-приложения (аналог AVSearch).
 */
public class MainApplication extends Application {

    /** Запасная версия, если манифест недоступен (например, при запуске из IDE). */
    private static final String FALLBACK_VERSION = "1.0.0";

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(MainApplication.class.getResource("main-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 960, 620);
        scene.getStylesheets().add(MainApplication.class.getResource("search-highlight.css").toExternalForm());
        scene.getStylesheets().add(MainApplication.class.getResource("theme-light.css").toExternalForm());
        // Контроллер загружает сохранённую тему только когда Scene уже создана.
        MainController controller = fxmlLoader.getController();
        controller.applySavedTheme(scene);
        stage.setTitle("all-search (" + applicationVersion() + ") — поиск по тексту в файлах");
        stage.getIcons().setAll(new Image(MainApplication.class.getResourceAsStream("icons/all-search.png")));
        stage.setMinWidth(640);
        stage.setMinHeight(400);
        stage.setScene(scene);
        stage.show();
    }

    /** Версия приложения: берётся из манифеста, при его отсутствии — запасная. */
    private static String applicationVersion() {
        Package pkg = MainApplication.class.getPackage();
        String version = pkg == null ? null : pkg.getImplementationVersion();
        return version == null || version.isBlank() ? FALLBACK_VERSION : version;
    }
}
