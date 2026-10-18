package ru.sarteam.allsearch;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class LongSyntaxTest {
    @Test
    void handlesLongStringsAndComments() {
        String body = "a".repeat(100_000);
        for (String extension : new String[]{"json", "java", "css", "xml"}) {
            for (String text : new String[]{"\"" + body + "\"", "/*" + body + "*/", "<!--" + body + "-->"}) {
                assertEquals(text.length(), new SyntaxHighlighter()
                        .build(Path.of("file." + extension), text, "", false, true).length());
            }
        }
    }
}
