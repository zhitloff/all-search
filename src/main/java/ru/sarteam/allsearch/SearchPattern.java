package ru.sarteam.allsearch;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Общие правила буквального и регулярного поиска по отдельным строкам. */
final class SearchPattern {
    private final Pattern pattern;

    SearchPattern(String query, boolean regex, boolean caseSensitive) {
        String text = query == null ? "" : query;
        int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
        pattern = text.isEmpty() ? null : Pattern.compile(regex ? text : Pattern.quote(text), flags);
    }

    boolean matches(String line) {
        return pattern != null && pattern.matcher(line).find();
    }

    /** Нулевые совпадения участвуют в отборе строк, но не выделяются в UI. */
    List<SearchResultNavigator.Hit> findHits(String text) {
        List<SearchResultNavigator.Hit> hits = new ArrayList<>();
        if (pattern == null) {
            return hits;
        }
        Matcher matcher = pattern.matcher(text);
        int start = 0;
        while (start < text.length()) {
            int end = start;
            while (end < text.length() && text.charAt(end) != '\n' && text.charAt(end) != '\r') {
                end++;
            }
            matcher.region(start, end);
            while (matcher.find()) {
                if (matcher.end() > matcher.start()) {
                    hits.add(new SearchResultNavigator.Hit(matcher.start(), matcher.end()));
                }
            }
            start = end + 1;
            if (end < text.length() && text.charAt(end) == '\r'
                    && start < text.length() && text.charAt(start) == '\n') {
                start++;
            }
        }
        return hits;
    }
}
