package ru.sarteam.allsearch;

import org.junit.jupiter.api.Test;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class ApplicationIconTest {
    @Test void killsTimedOutConversion() throws Exception {
        Process process = new ProcessBuilder(pathToJava(), "-cp", System.getProperty("java.class.path"),
                Sleeper.class.getName()).start();
        try {
            assertEquals('R', process.getInputStream().read());
            assertFalse(ApplicationIcon.waitFor(process, 50, TimeUnit.MILLISECONDS));
            assertTrue(process.waitFor(5, TimeUnit.SECONDS));
        } finally {
            process.destroyForcibly();
        }
    }

    private String pathToJava() {
        return java.nio.file.Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
    }

    public static class Sleeper {
        public static void main(String[] args) throws Exception {
            System.out.print('R');
            System.out.flush();
            Thread.sleep(60_000);
        }
    }
}
