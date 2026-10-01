package ru.mediadestroy.auth.storage;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

public final class PasswordUtil {

    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordUtil() {
    }

    public static String generateSalt() {
        byte[] saltBytes = new byte[16];
        RANDOM.nextBytes(saltBytes);
        return Base64.getEncoder().encodeToString(saltBytes);
    }

    public static String hash(String password, String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(Base64.getDecoder().decode(salt));
            byte[] hashed = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 недоступен", e);
        }
    }

    /**
     * "/reg пароль пароль" и "/l пароль пароль" - это один пароль, введённый дважды (привычка с других серверов).
     * Пробелы по краям и двойные пробелы внутри не считаются.
     */
    public static String normalize(String raw) {
        if (raw == null) return "";
        String p = raw.trim().replaceAll("\\s+", " ");
        String[] parts = p.split(" ");
        if (parts.length == 2 && parts[0].equals(parts[1])) return parts[0];
        return p;
    }

    public static boolean matches(String password, PlayerRecord record) {
        if (record == null || record.getSalt() == null || record.getHash() == null) return false;
        String n = normalize(password);
        if (check(n, record) || check(password, record)) return true;
        // старая ошибка: при "/reg пароль пароль" сохранялся пароль "пароль пароль" - пускаем и по одному слову
        return !n.contains(" ") && check(n + " " + n, record);
    }

    private static boolean check(String password, PlayerRecord record) {
        return hash(password, record.getSalt()).equals(record.getHash());
    }
}
