package ru.sarteam.allsearch;

import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import org.fxmisc.richtext.CodeArea;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Панель содержимого: текст, подсветка, номера строк, шрифт и прокрутка. */
final class FileContentView {
    private static final int MAX_DISPLAY_CHARS = 2_000_000;
    private final CodeArea content = new CodeArea();
    private final LineNumberView numbers;
    private final SyntaxHighlighter highlighter = new SyntaxHighlighter();
    private final ScrollPane scroll;
    private final HBox row;
    private String family = "monospace";
    private int size = 14;
    private int lineCount = 1;
    private double rowWidth;
    private int activeOffset = -1;

    FileContentView(ScrollPane scroll, HBox row, Label numbers, Label highlightedNumber) {
        this.scroll = scroll;
        this.row = row;
        this.numbers = new LineNumberView(numbers, highlightedNumber);
        content.setEditable(false);
        content.setFocusTraversable(true);
        content.getStyleClass().add("content-area");
        HBox.setHgrow(content, Priority.ALWAYS);
        row.getChildren().add(content);
        scroll.viewportBoundsProperty().addListener((obs, oldValue, newValue) -> updateRowWidth());
        row.addEventFilter(ScrollEvent.SCROLL, this::forwardScroll);
        ContextMenu menu = new ContextMenu();
        MenuItem copy = new MenuItem("Копировать");
        copy.setOnAction(event -> content.copy());
        menu.getItems().add(copy);
        content.setOnContextMenuRequested(event -> {
            copy.setDisable(content.getSelectedText().isEmpty());
            menu.show(content, event.getScreenX(), event.getScreenY());
        });
        setFont(family, size);
    }

    List<SearchResultNavigator.Hit> showFile(Path file, SearchPattern pattern) throws IOException {
        String text = SearchEngine.readFileContent(file).replace("\r\n", "\n").replace('\r', '\n');
        if (text.length() > MAX_DISPLAY_CHARS) {
            int length = text.length();
            text = text.substring(0, MAX_DISPLAY_CHARS)
                    + "\n\n… (файл обрезан для отображения: всего символов " + length + ")";
        }
        activeOffset = -1;
        content.replaceText(text);
        content.setStyleSpans(0, highlighter.build(file, text, pattern));
        lineCount = 1 + (int) text.chars().filter(character -> character == '\n').count();
        numbers.setLineCount(lineCount);
        updateMetrics();
        return pattern.findHits(text);
    }

    void clear() {
        activeOffset = -1;
        lineCount = 1;
        numbers.clear();
        content.clear();
        rowWidth = 0;
        updateRowWidth();
    }

    void clearHighlight() {
        activeOffset = -1;
        numbers.highlight(0);
        content.deselect();
    }

    void focusHit(SearchResultNavigator.Hit hit) {
        activeOffset = hit.start();
        content.requestFocus();
        content.selectRange(hit.start(), hit.end());
        int paragraph = content.offsetToPosition(hit.start(), org.fxmisc.richtext.model.TwoDimensional.Bias.Forward)
                .getMajor();
        numbers.highlight(paragraph + 1);
        scrollToParagraph(paragraph);
    }

    void setFont(String family, int size) {
        this.family = family == null || family.isBlank() ? "monospace" : family;
        this.size = Math.max(8, Math.min(72, size));
        content.setStyle("-fx-font-family: '" + this.family.replace("'", "\\'")
                + "'; -fx-font-size: " + this.size + "px;");
        numbers.setFont(this.family, this.size);
        updateMetrics();
    }

    private void updateMetrics() {
        double height = numbers.prefHeight();
        double areaHeight = height + 3 * Math.max(1, height / lineCount);
        content.setPrefHeight(areaHeight);
        content.setMinHeight(areaHeight);
        Text probe = new Text();
        probe.setFont(Font.font(family, size));
        double width = 0;
        for (String line : content.getText().split("\n", -1)) {
            probe.setText(line);
            width = Math.max(width, probe.getBoundsInLocal().getWidth());
        }
        rowWidth = numbers.prefWidth() + width;
        updateRowWidth();
        content.requestLayout();
        if (activeOffset >= 0) {
            int paragraph = content.offsetToPosition(activeOffset,
                    org.fxmisc.richtext.model.TwoDimensional.Bias.Forward).getMajor();
            scrollToParagraph(paragraph);
        }
    }

    private void updateRowWidth() {
        double width = Math.max(scroll.getViewportBounds().getWidth(), rowWidth);
        row.setPrefWidth(width);
        row.setMinWidth(width);
    }

    private void scrollToParagraph(int paragraph) {
        if (scroll.getContent() == null) {
            return;
        }
        double height = scroll.getContent().getBoundsInLocal().getHeight();
        double viewport = scroll.getViewportBounds().getHeight();
        if (height > viewport && viewport > 0) {
            double center = (Math.max(0, Math.min(lineCount - 1, paragraph)) + 0.5) * height / lineCount;
            scroll.setVvalue(clamp((center - viewport / 2) / (height - viewport)));
        }
    }

    private void forwardScroll(ScrollEvent event) {
        if (scroll.getContent() == null) {
            return;
        }
        double height = scroll.getContent().getBoundsInLocal().getHeight() - scroll.getViewportBounds().getHeight();
        double width = scroll.getContent().getBoundsInLocal().getWidth() - scroll.getViewportBounds().getWidth();
        boolean moved = false;
        if (height > 0 && event.getDeltaY() != 0) {
            scroll.setVvalue(clamp(scroll.getVvalue() - event.getDeltaY() / Math.max(1, height)));
            moved = true;
        }
        if (width > 0 && event.getDeltaX() != 0) {
            scroll.setHvalue(clamp(scroll.getHvalue() - event.getDeltaX() / Math.max(1, width)));
            moved = true;
        }
        if (moved) {
            event.consume();
        }
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }
}
