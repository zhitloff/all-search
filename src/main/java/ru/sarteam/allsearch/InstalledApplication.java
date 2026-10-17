package ru.sarteam.allsearch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/** Поиск приложений, доступных для команды «Открыть с помощью...». */
final class InstalledApplication {
    private InstalledApplication() {
    }

    static List<Entry> find() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        Map<String, Entry> applications = new LinkedHashMap<>();
        if (os.contains("mac")) {
            addMacApplications(applications);
        } else if (os.contains("win")) {
            addWindowsApplications(applications);
        } else {
            addLinuxApplications(applications);
        }
        return applications.values().stream()
                .sorted(Comparator.comparing(Entry::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    static boolean matches(Entry application, String query) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return true;
        }
        String name = application.name().toLowerCase(Locale.ROOT);
        String path = application.path().toString().toLowerCase(Locale.ROOT);
        return name.contains(normalized)
                || path.contains(normalized)
                || abbreviation(name).contains(normalized);
    }

    private static String abbreviation(String name) {
        StringBuilder result = new StringBuilder();
        boolean wordStart = true;
        for (int i = 0; i < name.length(); i++) {
            char character = name.charAt(i);
            if (Character.isLetterOrDigit(character)) {
                if (wordStart) {
                    result.append(character);
                }
                wordStart = false;
            } else {
                wordStart = true;
            }
        }
        return result.toString();
    }

    static void launch(Entry application, Path file) throws IOException {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("mac")) {
            new ProcessBuilder("open", "-a", application.path().toString(), file.toString()).start();
        } else if (application.path().toString().toLowerCase(Locale.ROOT).endsWith(".lnk")) {
            new ProcessBuilder("cmd", "/c", "start", "", application.path().toString(), file.toString()).start();
        } else {
            new ProcessBuilder(application.path().toString(), file.toString()).start();
        }
    }

    private static void addMacApplications(Map<String, Entry> result) {
        addApplications(result, Path.of("/Applications"), ".app", 2);
        addApplications(result, Path.of(System.getProperty("user.home"), "Applications"), ".app", 2);
        addApplications(result, Path.of("/System/Applications"), ".app", 2);
    }

    private static void addWindowsApplications(Map<String, Entry> result) {
        addApplications(result, pathFromEnvironment("ProgramFiles"), ".exe", 3);
        addApplications(result, pathFromEnvironment("ProgramFiles(x86)"), ".exe", 3);
        addApplications(result, pathFromEnvironment("LOCALAPPDATA"), ".exe", 4);
        addApplications(result, pathFromEnvironment("APPDATA"), ".lnk", 5);
        addApplications(result, pathFromEnvironment("PROGRAMDATA"), ".lnk", 5);
    }

    private static void addLinuxApplications(Map<String, Entry> result) {
        addApplications(result, Path.of("/usr/share/applications"), ".desktop", 1);
        addApplications(result, Path.of(System.getProperty("user.home"), ".local/share/applications"), ".desktop", 1);
    }

    private static void addApplications(Map<String, Entry> result, Path root, String suffix, int depth) {
        if (root == null || !Files.isDirectory(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root, depth)) {
            paths.filter(path -> path.getFileName() != null)
                    .filter(path -> path.toString().toLowerCase(Locale.ROOT).endsWith(suffix))
                    .forEach(path -> result.putIfAbsent(path.toAbsolutePath().normalize().toString(),
                            new Entry(displayName(path), path)));
        } catch (IOException | SecurityException ignored) {
            // Недоступный каталог не должен мешать открытию файла.
        }
    }

    private static Path pathFromEnvironment(String name) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? null : Path.of(value);
    }

    private static String displayName(Path path) {
        String name = path.getFileName().toString();
        if (name.endsWith(".app") || name.endsWith(".exe") || name.endsWith(".lnk") || name.endsWith(".desktop")) {
            return name.substring(0, name.lastIndexOf('.'));
        }
        return name;
    }

    record Entry(String name, Path path) {
        @Override
        public String toString() {
            return name;
        }
    }
}
