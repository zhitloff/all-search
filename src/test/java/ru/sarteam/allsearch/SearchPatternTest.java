package ru.sarteam.allsearch;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class SearchPatternTest {
    @Test
    void quotesLiteralTextAndUsesUnicodeCaseRules() {
        SearchPattern pattern = new SearchPattern("ЁЖ[.]", false, false);
        assertTrue(pattern.matches("ёж[.]"));
        assertFalse(pattern.matches("ёжx"));
        assertEquals(List.of(new SearchResultNavigator.Hit(0, 5)), pattern.findHits("ёж[.]"));
        assertFalse(new SearchPattern("ЁЖ", false, true).matches("ёж"));
    }

    @Test
    void usesLineBoundariesAndDocumentOffsets() {
        SearchPattern pattern = new SearchPattern("^foo$", true, true);
        assertEquals(List.of(new SearchResultNavigator.Hit(0, 3), new SearchResultNavigator.Hit(5, 8),
                new SearchResultNavigator.Hit(9, 12)), pattern.findHits("foo\r\nfoo\rfoo\nother"));
        assertTrue(new SearchPattern("foo\\s+bar", true, true).findHits("foo\nbar").isEmpty());
    }

    @Test
    void doesNotHighlightZeroLengthOrEmptyQueries() {
        SearchPattern pattern = new SearchPattern("^", true, true);
        assertTrue(pattern.matches("text"));
        assertTrue(pattern.findHits("text\ntext").isEmpty());
        assertTrue(new SearchPattern("", false, false).findHits("text").isEmpty());
    }
}
