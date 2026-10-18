package ru.sarteam.allsearch;

import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.model.StyleSpans;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

/** Виртуализированный просмотр ограниченного фрагмента файла. */
final class FileContentView {
    static final int MAX_PREVIEW_BYTES = 2_000_000;
    private final CodeArea content = new CodeArea();
    private int activeParagraph = -1;

    record Prepared(String text, StyleSpans<Collection<String>> styles,
                    List<SearchResultNavigator.Hit> hits) {}

    FileContentView(ScrollPane scroll, HBox row, Label numbers, Label highlightedNumber) {
        content.setEditable(false);
        content.getStyleClass().add("content-area");
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setContent(new VirtualizedScrollPane<>(content));
        updateNumbers();
        MenuItem copy = new MenuItem("Копировать");
        copy.setOnAction(event -> content.copy());
        ContextMenu menu = new ContextMenu(copy);
        menu.setOnShowing(event -> copy.setDisable(content.getSelectedText().isEmpty()));
        content.setContextMenu(menu);
        setFont(FontConstants.DEFAULT_FONT_FAMILY, FontConstants.DEFAULT_FONT_SIZE);
    }

    static Prepared prepare(Path file, SearchPattern pattern) throws IOException {
        String text = TextFileReader.readPreview(file, MAX_PREVIEW_BYTES)
                .replace("\r\n", "\n").replace('\r', '\n');
        return new Prepared(text, new SyntaxHighlighter().build(file, text, pattern), pattern.findHits(text));
    }

    List<SearchResultNavigator.Hit> show(Prepared prepared) {
        activeParagraph = -1;
        content.replaceText(prepared.text());
        content.setStyleSpans(0, prepared.styles());
        updateNumbers();
        return prepared.hits();
    }

    void clear() {
        activeParagraph = -1;
        content.clear();
        updateNumbers();
    }

    void clearHighlight() {
        activeParagraph = -1;
        content.deselect();
        updateNumbers();
    }

    void focusHit(SearchResultNavigator.Hit hit) {
        content.selectRange(hit.start(), hit.end());
        activeParagraph = content.getCurrentParagraph();
        updateNumbers();
        content.showParagraphAtCenter(activeParagraph);
        content.requestFocus();
    }

    private void updateNumbers() {
        var factory = LineNumberFactory.get(content);
        content.setParagraphGraphicFactory(index -> {
            var node = factory.apply(index);
            if (index == activeParagraph) node.setStyle("-fx-background-color: #ffe08a; -fx-text-fill: black;");
            return node;
        });
    }

    void setFont(String family, int size) {
        String safeFamily = family == null || family.isBlank() ? FontConstants.DEFAULT_FONT_FAMILY : family;
        content.setStyle("-fx-font-family: '" + safeFamily.replace("'", "\\'")
                + "'; -fx-font-size: " + Math.max(8, Math.min(72, size)) + "px;");
    }
}
