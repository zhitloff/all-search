package ru.sarteam.allsearch;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Читает текстовые файлы и выбирает кодировку по содержимому. */
final class TextFileReader {

    private static final Charset WINDOWS_1251 = Charset.forName("windows-1251");
    private static final int BINARY_PROBE = 8192;

    private TextFileReader() {
    }

    static String read(Path file) throws IOException {
        return decodeBytes(Files.readAllBytes(file));
    }

    static byte[] readBytes(Path file) throws IOException {
        return Files.readAllBytes(file);
    }

    static boolean isBinary(byte[] bytes) {
        int n = Math.min(bytes.length, BINARY_PROBE);
        for (int i = 0; i < n; i++) {
            if (bytes[i] == 0) {
                return true;
            }
        }
        return false;
    }

    static boolean hasUtf16Bom(byte[] bytes) {
        return bytes.length >= 2
                && (((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xFE)
                || ((bytes[0] & 0xFF) == 0xFE && (bytes[1] & 0xFF) == 0xFF));
    }

    static String decodeBytes(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF
                && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
            return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        }
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xFE) {
            return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16LE);
        }
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFE && (bytes[1] & 0xFF) == 0xFF) {
            return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16BE);
        }
        if (isValidUtf8(bytes)) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return new String(bytes, WINDOWS_1251);
    }

    private static boolean isValidUtf8(byte[] bytes) {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            decoder.decode(ByteBuffer.wrap(bytes));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }
}
