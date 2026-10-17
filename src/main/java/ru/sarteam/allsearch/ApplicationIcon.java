package ru.sarteam.allsearch;

import javafx.scene.image.ImageView;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;

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

    static ImageView create(Path path) {
        try {
            if (isMacApplication(path)) {
                ImageView macIcon = createMacIcon(path);
                if (macIcon != null) {
                    return macIcon;
                }
            }
            Icon source = FileSystemView.getFileSystemView().getSystemIcon(path.toFile());
            int size = 24;
            BufferedImage rendered = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = rendered.createGraphics();
            source.paintIcon(null, graphics, 0, 0);
            graphics.dispose();

            WritableImage image = new WritableImage(size, size);
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    image.getPixelWriter().setArgb(x, y, rendered.getRGB(x, y));
                }
            }
            ImageView view = new ImageView(image);
            view.setFitWidth(size);
            view.setFitHeight(size);
            view.setPreserveRatio(true);
            return view;
        } catch (RuntimeException ignored) {
            return new ImageView();
        }
    }

    private static boolean isMacApplication(Path path) {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac")
                && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".app");
    }

    private static ImageView createMacIcon(Path application) {
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
                    .redirectErrorStream(true)
                    .start();
            if (process.waitFor() != 0 || !Files.isRegularFile(png)) {
                return null;
            }
            Image image = new Image(png.toUri().toString(), 24, 24, true, true, false);
            if (image.isError()) {
                return null;
            }
            ImageView view = new ImageView(image);
            view.setFitWidth(24);
            view.setFitHeight(24);
            view.setPreserveRatio(true);
            return view;
        } catch (IOException | InterruptedException | RuntimeException e) {
            Thread.currentThread().interrupt();
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
}
