module ru.sarteam.allsearch {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.swing;
    requires java.desktop;

    requires org.controlsfx.controls;
    requires org.kordamp.ikonli.javafx;

    requires org.fxmisc.richtext;
    requires org.fxmisc.flowless;

    opens ru.sarteam.allsearch to javafx.fxml;
    exports ru.sarteam.allsearch;
}
