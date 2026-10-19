module ru.sarteam.allsearch {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.swing;
    requires java.desktop;

    requires org.controlsfx.controls;
    requires org.kordamp.ikonli.javafx;

    requires org.fxmisc.richtext;
    requires org.fxmisc.flowless;

    // Нужен только для компиляции/запуска тестов по module path (например, в IntelliJ IDEA).
    // static — при запуске приложения JUnit в модульном графе не требуется.
    requires static org.junit.jupiter.api;

    // javafx.fxml — для FXML-инъекции, org.junit.platform.commons — для доступа JUnit
    // к тест-классам через рефлексию при запуске по module path.
    opens ru.sarteam.allsearch to javafx.fxml, org.junit.platform.commons;
    exports ru.sarteam.allsearch;
}
