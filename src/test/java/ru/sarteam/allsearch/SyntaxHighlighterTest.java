package ru.sarteam.allsearch;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.Collection;
import org.fxmisc.richtext.model.StyleSpans;
import org.junit.jupiter.api.Test;

class SyntaxHighlighterTest {
    private final SyntaxHighlighter highlighter = new SyntaxHighlighter();

    @Test
    void highlightsSupportedFormatsWithoutMissingGroups() {
        assertStyle("A.JAVA", "public class A {}", "syn-keyword");
        assertStyle("a.json", "{\"a\": true, \"b\": 42}", "syn-number");
        assertStyle("a.xml", "<tag attr=\"value\"><!-- comment --></tag>", "syn-attr");
        assertStyle("a.css", "a { color: \"red\"; width: 10px; }", "syn-property");
        assertStyle("a.md", "# Heading\n`code`", "syn-md-code");
    }

    @Test
    void combinesSearchAndSyntaxAndHandlesEmptyText() {
        var spans = highlighter.build(Path.of("a.java"), "public", "public", false, true);
        assertTrue(spans.getStyleSpan(0).getStyle().containsAll(java.util.List.of("syn-keyword", "search-hit")));
        assertEquals(0, highlighter.build(Path.of("a.txt"), "", "", false, false).length());
    }

    @Test
    void limitsSearchHighlightingToFiveThousandMatches() {
        String text = "x ".repeat(5_001);
        StyleSpans<Collection<String>> spans = highlighter.build(Path.of("a.txt"), text, "x", false, true);
        int highlighted = 0;
        for (var span : spans) {
            if (span.getStyle().contains("search-hit")) {
                highlighted += span.getLength();
            }
        }
        assertEquals(5_000, highlighted);
        assertEquals(text.length(), spans.length());
    }

    private void assertStyle(String name, String text, String style) {
        var spans = highlighter.build(Path.of(name), text, "", false, false);
        assertEquals(text.length(), spans.length());
        assertTrue(java.util.stream.StreamSupport.stream(spans.spliterator(), false)
                .anyMatch(span -> span.getStyle().contains(style)), name);
    }
}
