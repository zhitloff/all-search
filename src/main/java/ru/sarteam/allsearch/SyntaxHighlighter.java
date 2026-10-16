package ru.sarteam.allsearch;

import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Строит RichTextFX-стили для синтаксиса файла и совпадений поиска. */
final class SyntaxHighlighter {

    private static final int MAX_HIGHLIGHTS = 5_000;
    private static final byte NONE = 0, KEYWORD = 1, STRING = 2, COMMENT = 3, NUMBER = 4;
    private static final byte ANNOTATION = 5, TAG = 6, ATTR = 7, PUNCT = 8, PROPERTY = 9;
    private static final byte MD_HEADING = 10, MD_CODE = 11, MD_LINK = 12, MD_QUOTE = 13, MD_LIST = 14;

    private static final Pattern CODE = Pattern.compile(
            "(?<COMMENT>//[^\\n]*|/\\*(?:.|\\R)*?\\*/)|"
                    + "(?<STRING>\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*')|"
                    + "(?<ANNOT>@[A-Za-z_][A-Za-z0-9_]*)|"
                    + "(?<NUMBER>\\b\\d+(?:\\.\\d+)?\\b)|"
                    + "(?<KEYWORD>\\b(?:abstract|assert|boolean|break|byte|case|catch|char|class|const|continue|default|do|double|else|"
                    + "enum|extends|final|finally|float|for|goto|if|implements|import|instanceof|int|interface|long|native|new|package|"
                    + "private|protected|public|return|short|static|strictfp|super|switch|synchronized|this|throw|throws|transient|try|"
                    + "void|volatile|while|var|record|sealed|permits|yield|non-sealed|fun|val|let|fn|struct|impl|trait|use|namespace)\\b)", Pattern.MULTILINE);
    private static final Pattern JSON = Pattern.compile("(?<STRING>\"(?:\\\\.|[^\"\\\\])*\")|(?<NUMBER>-?\\b\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?\\b)|(?<KEYWORD>\\b(?:true|false|null)\\b)|(?<PUNCT>[{}\\[\\]:,])");
    private static final Pattern XML = Pattern.compile("(?<COMMENT><!--(?:.|\\R)*?-->)|(?<TAG></?[A-Za-z_:][A-Za-z0-9:._-]*)|(?<ATTR>[A-Za-z_:][A-Za-z0-9:._-]*(?=\\s*=))|(?<STRING>\"[^\"]*\"|'[^']*')", Pattern.MULTILINE);
    private static final Pattern CSS = Pattern.compile("(?<COMMENT>/\\*(?:.|\\R)*?\\*/)|(?<PROPERTY>\\b[a-zA-Z-]+(?=\\s*:))|(?<NUMBER>\\b\\d+(?:\\.\\d+)?(?:px|em|rem|%|vh|vw|s|ms)?\\b)|(?<PUNCT>[{}:;(),.])|(?<STRING>\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*')", Pattern.MULTILINE);
    private static final Pattern MD_FENCE_PATTERN = Pattern.compile("(?ms)^```.*?$.*?^```\\s*$");
    private static final Pattern MD_HEADING_PATTERN = Pattern.compile("(?m)^#{1,6}\\s.*$");
    private static final Pattern MD_QUOTE_PATTERN = Pattern.compile("(?m)^>.*$");
    private static final Pattern MD_LIST_PATTERN = Pattern.compile("(?m)^\\s*(?:[-*+]\\s+.*|\\d+\\.\\s+.*)$");
    private static final Pattern MD_LINK_PATTERN = Pattern.compile("\\[[^\\]]+\\]\\([^\\)]+\\)");
    private static final Pattern MD_INLINE_PATTERN = Pattern.compile("`[^`\\n]+`");

    StyleSpans<Collection<String>> build(Path file, String text, String query, boolean regex, boolean caseSensitive) {
        if (text.isEmpty()) return new StyleSpansBuilder<Collection<String>>().create();
        byte[] syntax = new byte[text.length()];
        byte[] search = new byte[text.length()];
        applySyntax(file, text, syntax);
        if (query != null && !query.isEmpty()) {
            Pattern p = Pattern.compile(regex ? query : Pattern.quote(query), caseSensitive ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            Matcher m = p.matcher(text);
            for (int count = 0; m.find() && count < MAX_HIGHLIGHTS; count++) if (m.end() > m.start()) paint(search, m.start(), m.end(), (byte) 1, true);
        }
        StyleSpansBuilder<Collection<String>> out = new StyleSpansBuilder<>();
        int start = 0, key = ((syntax[0] & 0xff) << 1) | search[0];
        for (int i = 1; i < text.length(); i++) {
            int next = ((syntax[i] & 0xff) << 1) | search[i];
            if (next != key) { out.add(classes(key), i - start); start = i; key = next; }
        }
        out.add(classes(key), text.length() - start);
        return out.create();
    }

    private static void applySyntax(Path file, String text, byte[] target) {
        String ext = extension(file);
        if (ext.equals("md") || ext.equals("markdown")) {
            paintPattern(MD_FENCE_PATTERN, text, target, MD_CODE); paintPattern(MD_HEADING_PATTERN, text, target, MD_HEADING);
            paintPattern(MD_QUOTE_PATTERN, text, target, MD_QUOTE); paintPattern(MD_LIST_PATTERN, text, target, MD_LIST);
            paintPattern(MD_LINK_PATTERN, text, target, MD_LINK); paintPattern(MD_INLINE_PATTERN, text, target, MD_CODE); return;
        }
        Pattern pattern = ext.equals("json") || ext.equals("json5") ? JSON
                : ext.equals("xml") || ext.equals("html") || ext.equals("htm") || ext.equals("xhtml") || ext.equals("svg") ? XML
                : ext.equals("css") || ext.equals("scss") || ext.equals("less") ? CSS
                : isCode(ext) ? CODE : null;
        if (pattern == null) return;
        Matcher m = pattern.matcher(text);
        while (m.find()) {
            byte style = m.group("COMMENT") != null ? COMMENT : m.group("STRING") != null ? STRING
                    : m.group("ANNOT") != null ? ANNOTATION : m.group("NUMBER") != null ? NUMBER
                    : m.group("KEYWORD") != null ? KEYWORD : m.group("TAG") != null ? TAG
                    : m.group("ATTR") != null ? ATTR : m.group("PROPERTY") != null ? PROPERTY
                    : m.group("PUNCT") != null ? PUNCT : NONE;
            if (style != NONE) paint(target, m.start(), m.end(), style, false);
        }
    }

    private static void paintPattern(Pattern p, String text, byte[] target, byte style) { Matcher m = p.matcher(text); while (m.find()) if (m.end() > m.start()) paint(target, m.start(), m.end(), style, false); }
    private static void paint(byte[] target, int start, int end, byte style, boolean overwrite) { for (int i = Math.max(0, start); i < Math.min(target.length, end); i++) if (overwrite || target[i] == NONE) target[i] = style; }
    private static Collection<String> classes(int key) { String style = styleName((byte) ((key >> 1) & 0x7f)); boolean hit = (key & 1) != 0; if (style == null) return hit ? Collections.singleton("search-hit") : Collections.emptyList(); return hit ? Arrays.asList(style, "search-hit") : Collections.singleton(style); }
    private static String styleName(byte style) { return switch (style) { case KEYWORD -> "syn-keyword"; case STRING -> "syn-string"; case COMMENT -> "syn-comment"; case NUMBER -> "syn-number"; case ANNOTATION -> "syn-annotation"; case TAG -> "syn-tag"; case ATTR -> "syn-attr"; case PUNCT -> "syn-punct"; case PROPERTY -> "syn-property"; case MD_HEADING -> "syn-md-heading"; case MD_CODE -> "syn-md-code"; case MD_LINK -> "syn-md-link"; case MD_QUOTE -> "syn-md-quote"; case MD_LIST -> "syn-md-list"; default -> null; }; }
    private static String extension(Path file) { if (file == null || file.getFileName() == null) return ""; String n = file.getFileName().toString(); int dot = n.lastIndexOf('.'); return dot < 0 || dot == n.length() - 1 ? "" : n.substring(dot + 1).toLowerCase(); }
    private static boolean isCode(String e) { return e.matches("java|kt|kts|js|jsx|ts|tsx|c|h|cpp|hpp|cs|go|rs|swift|py|php|sql|groovy"); }
}
