package ru.sarteam.allsearch;

import javafx.scene.control.Label;

import java.util.Locale;

/** Отображает нумерацию строк и активный номер поверх базовой нумерации. */
final class LineNumberView {
    private final Label numbers;
    private final Label highlightedNumber;
    private int lineCount = 1;

    LineNumberView(Label numbers, Label highlightedNumber) {
        this.numbers = numbers;
        this.highlightedNumber = highlightedNumber;
    }

    void clear() {
        lineCount = 1;
        numbers.setText("");
        highlightedNumber.setText("");
    }

    void setLineCount(int count) {
        lineCount = Math.max(1, count);
        int width = String.valueOf(lineCount).length();
        numbers.setText(buildNumbers(lineCount, width));
        highlightedNumber.setText("");
    }

    void highlight(int line) {
        if (line < 1 || line > lineCount) {
            highlightedNumber.setText("");
            return;
        }
        int width = String.valueOf(lineCount).length();
        highlightedNumber.setText(buildHighlightedNumbers(lineCount, line, width));
    }

    double prefHeight() {
        return numbers.prefHeight(-1);
    }

    double prefWidth() {
        return numbers.prefWidth(-1);
    }

    void setFont(String family, int size) {
        String safeFamily = family == null || family.isBlank() ? "monospace" : family;
        String escaped = safeFamily.replace("'", "\\'");
        String style = "-fx-font-family: '" + escaped + "'; -fx-font-size: "
                + Math.max(8, Math.min(72, size)) + "px;";
        numbers.setStyle(style);
        highlightedNumber.setStyle(style);
    }

    private static String buildNumbers(int count, int width) {
        String format = "%" + width + "d";
        StringBuilder result = new StringBuilder(count * (width + 1));
        for (int i = 1; i <= count; i++) {
            if (i > 1) result.append('\n');
            result.append(String.format(Locale.ROOT, format, i));
        }
        return result.toString();
    }

    private static String buildHighlightedNumbers(int count, int highlighted, int width) {
        String format = "%" + width + "d";
        String blank = " ".repeat(width);
        StringBuilder result = new StringBuilder(count * (width + 1));
        for (int i = 1; i <= count; i++) {
            if (i > 1) result.append('\n');
            result.append(i == highlighted ? String.format(Locale.ROOT, format, i) : blank);
        }
        return result.toString();
    }
}
