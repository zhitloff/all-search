package ru.sarteam.allsearch;

import javafx.application.Application;

/**
 * Не-модульная точка входа (для запуска вне javafx-maven-plugin, например из IDE).
 * Внимание: в pom.xml mainClass плагина указывает на {@link MainApplication}.
 */
public class Launcher {
    public static void main(String[] args) {
        Application.launch(MainApplication.class, args);
    }
}
