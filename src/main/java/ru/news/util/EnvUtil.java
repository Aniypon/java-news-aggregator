package ru.news.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Утилита для чтения переменных окружения из .env файла
 */
public class EnvUtil {
    private static final Logger logger = LoggerFactory.getLogger(EnvUtil.class);
    private static final String ENV_FILE = ".env";
    private static final Map<String, String> envVars = new HashMap<>();
    
    static {
        loadEnvFile();
    }
    
    /**
     * Загружает переменные из .env файла
     */
    private static void loadEnvFile() {
        try {
            Path envPath = Paths.get(ENV_FILE);
            if (!Files.exists(envPath)) {
                logger.warn("Файл .env не найден");
                return;
            }
            
            try (Stream<String> lines = Files.lines(envPath)) {
                lines.forEach(line -> {
                    if (line.trim().isEmpty() || line.startsWith("#")) {
                        return;
                    }
                    
                    String[] parts = line.split("=", 2);
                    if (parts.length == 2) {
                        envVars.put(parts[0].trim(), parts[1].trim());
                    }
                });
            }
            
            logger.info("Успешно загружены переменные из .env файла");
        } catch (IOException e) {
            logger.error("Ошибка при чтении .env файла", e);
        }
    }
    
    /**
     * Получает значение переменной окружения сначала из системных переменных, 
     * затем из .env файла
     */
    public static String getEnv(String key) {
        // Сначала проверяем системные переменные
        String value = System.getenv(key);
        if (value == null) {
            // Затем проверяем .env файл
            value = envVars.get(key);
        }
        return value;
    }
    
    /**
     * Получает значение переменной окружения с возможностью указать значение по умолчанию
     */
    public static String getEnv(String key, String defaultValue) {
        String value = getEnv(key);
        return value != null ? value : defaultValue;
    }
}