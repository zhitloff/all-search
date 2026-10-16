package ru.sarteam.allsearch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.regex.PatternSyntaxException;

import org.junit.jupiter.api.Test;

class SearchOptionsFactoryTest {

    private final SearchOptionsFactory factory = new SearchOptionsFactory();

    @Test
    void createsOptionsFromControlValues() {
        SearchEngine.Options options = factory.create(
                "needle", true, true, "*.java, *.kt;*.xml  README.md", true);

        assertEquals("needle", options.query());
        assertTrue(options.regex());
        assertTrue(options.caseSensitive());
        assertEquals(List.of("*.java", "*.kt", "*.xml", "README.md"), options.masks());
        assertTrue(options.textOnly());
        assertEquals(SearchEngine.DEFAULT_MAX_RESULTS, options.maxResults());
    }

    @Test
    void acceptsEmptyMasksAndNullQuery() {
        SearchEngine.Options options = factory.create(null, false, false, "  ", false);

        assertEquals("", options.query());
        assertEquals(List.of(), options.masks());
        assertFalse(options.regex());
        assertFalse(options.caseSensitive());
        assertFalse(options.textOnly());
    }

    @Test
    void rejectsInvalidRegularExpression() {
        assertThrows(PatternSyntaxException.class,
                () -> factory.create("[", true, false, "", true));
    }
}
