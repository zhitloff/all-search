package ru.sarteam.allsearch;

import java.util.ArrayList;
import java.util.List;

/** Создаёт параметры поиска из значений элементов управления. */
final class SearchOptionsFactory {

    SearchEngine.Options create(String query, boolean regex, boolean caseSensitive,
                                String masksText, boolean textOnly) {
        String safeQuery = query == null ? "" : query;
        new SearchPattern(safeQuery, regex, caseSensitive);
        return new SearchEngine.Options(
                safeQuery,
                regex,
                caseSensitive,
                parseMasks(masksText),
                textOnly,
                0);
    }

    private static List<String> parseMasks(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> masks = new ArrayList<>();
        for (String part : text.split("[;,]\\s*|\\s+")) {
            if (!part.isBlank()) {
                masks.add(part);
            }
        }
        return masks;
    }
}
