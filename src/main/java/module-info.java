module ru.sarteam.allsearch {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.controlsfx.controls;
    requires org.kordamp.ikonli.javafx;

    opens ru.sarteam.allsearch to javafx.fxml;
    exports ru.sarteam.allsearch;
}