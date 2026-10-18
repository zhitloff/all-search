package ru.sarteam.allsearch;

import javafx.scene.image.Image;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.nio.file.Files;
import java.io.IOException;
import java.util.Locale;
import java.util.stream.Stream;
import javax.swing.Icon;
import javax.swing.filechooser.FileSystemView;

/** Получает системную иконку установленного приложения. */
final class ApplicationIcon {
    private ApplicationIcon() {
    }

    static Image create(Path path) {
        try {
            if (isMacApplication(path)) {
                Image macIcon = createMacIcon(path);
                if (macIcon != null) {
                    return macIcon;
                }
            }
            Icon source = FileSystemView.getFileSystemView().getSystemIcon(path.toFile());
            if (source == null) return null;
            int size = 24;
            BufferedImage rendered = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = rendered.createGraphics();
            source.paintIcon(null, graphics, 0, 0);
            graphics.dispose();

            return javafx.embed.swing.SwingFXUtils.toFXImage(rendered, null);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static boolean isMacApplication(Path path) {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac")
                && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".app");
    }

    private static Image createMacIcon(Path application) {
        Path icon;
        try (Stream<Path> files = Files.walk(application.resolve("Contents/Resources"), 3)) {
            icon = files.filter(file -> file.toString().toLowerCase(Locale.ROOT).endsWith(".icns"))
                    .findFirst().orElse(null);
        } catch (IOException | SecurityException e) {
            return null;
        }
        if (icon == null) {
            return null;
        }

        Path png = null;
        try {
            png = Files.createTempFile("all-search-icon-", ".png");
            Process process = new ProcessBuilder("sips", "-s", "format", "png",
                    icon.toString(), "--out", png.toString())
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            if (!waitFor(process, 5, java.util.concurrent.TimeUnit.SECONDS) || !Files.isRegularFile(png)) {
                return null;
            }
            Image image = new Image(png.toUri().toString(), 24, 24, true, true, false);
            if (image.isError()) {
                return null;
            }
            return image;
        } catch (IOException | InterruptedException | RuntimeException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        } finally {
            if (png != null) {
                try {
                    Files.deleteIfExists(png);
                } catch (IOException ignored) {
                    // Временный файл будет удалён системой позже.
                }
            }
        }
    }

    static boolean waitFor(Process process, long timeout, java.util.concurrent.TimeUnit unit)
            throws InterruptedException {
        try {
            return process.waitFor(timeout, unit) && process.exitValue() == 0;
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }
}
