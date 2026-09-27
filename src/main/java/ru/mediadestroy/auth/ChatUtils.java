package ru.mediadestroy.auth;

import java.util.HashMap;
import java.util.Map;

/**
 * Центрирование строк в чате Minecraft.
 * Чат в ванильном клиенте по умолчанию имеет ширину примерно 320 пикселей
 * (стандартный размер шрифта, без учёта масштабирования интерфейса игрока).
 * Каждый символ имеет свою пиксельную ширину — используем таблицу для
 * основных символов кириллицы/латиницы/цифр/пунктуации.
 */
public final class ChatUtils {

    private static final int CHAT_WIDTH_PX = 320;
    private static final int SPACE_WIDTH_PX = 4;
    private static final int DEFAULT_CHAR_WIDTH_PX = 6;

    private static final Map<Character, Integer> WIDTHS = new HashMap<>();

    static {
        put(4, 'i', 'l', '.', ',', ':', ';', '\'', '!', '|');
        put(5, '`', 'I', '[', ']', 't', ' ');
        put(6, ' ', 'k', 'f', '"', '(', ')', '*', '<', '>', '{', '}');
        put(7, 'a', 'b', 'c', 'd', 'e', 'g', 'h', 'j', 'm', 'n', 'o', 'p',
                'q', 'r', 's', 'u', 'v', 'w', 'x', 'y', 'z',
                'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'J', 'K', 'L',
                'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W',
                'X', 'Y', 'Z', '0', '1', '2', '3', '4', '5', '6', '7',
                '8', '9', '-', '+', '=', '_', '?', '/', '\\', '%', '#',
                '@', '$', '^', '&');
    }

    private static void put(int width, Character... chars) {
        for (char c : chars) {
            WIDTHS.put(c, width);
        }
    }

    private ChatUtils() {
    }

    /**
     * Пиксельная ширина строки (без цветовых кодов — их нужно убрать заранее,
     * например через PlainTextComponentSerializer).
     */
    public static int width(String plain) {
        int total = 0;
        for (char c : plain.toCharArray()) {
            total += WIDTHS.getOrDefault(c, DEFAULT_CHAR_WIDTH_PX);
        }
        return total;
    }

    /**
     * Возвращает строку из пробелов, которую нужно поставить перед текстом,
     * чтобы визуально отцентрировать его в чате. Если текст шире чата —
     * возвращает пустую строку (центрировать нечего).
     */
    public static String centerPad(String plain) {
        int textWidth = width(plain);
        int freeSpace = CHAT_WIDTH_PX - textWidth;
        if (freeSpace <= 0) {
            return "";
        }
        int spaces = (freeSpace / 2) / SPACE_WIDTH_PX;
        if (spaces <= 0) {
            return "";
        }
        return " ".repeat(spaces);
    }
}
