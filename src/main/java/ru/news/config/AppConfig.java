package ru.news.config;

import ru.news.util.EnvUtil;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Конфигурация приложения
 * Реализует паттерн Singleton для управления настройками
 */

public class AppConfig {
    private Properties properties;

    public AppConfig() {
        loadProperties();
    }

    private void loadProperties() {
        properties = new Properties();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("application.properties")) {
            if (input == null) {
                throw new RuntimeException("Файл application.properties не найден");
            }
            properties.load(input);
        } catch (IOException e) {
            throw new RuntimeException("Ошибка при загрузке конфигурации", e);
        }
    }

    public String getBotToken() {
        return EnvUtil.getEnv("BOT_TOKEN", properties.getProperty("bot.token"));
    }

    public String getBotUsername() {
        return EnvUtil.getEnv("BOT_USERNAME", properties.getProperty("bot.username"));
    }

    public String getDatabasePath() {
        return properties.getProperty("database.path", "news.db");
    }

    public int getMaxNewsPerRequest() {
        return Integer.parseInt(properties.getProperty("news.max.per.request", "5"));
    }

    public int getParsingIntervalMinutes() {
        return Integer.parseInt(properties.getProperty("parsing.interval.minutes", "30"));
    }

    public String getOpenRouterApiKey() {
        return EnvUtil.getEnv("OPENROUTER_API_KEY");
    }

    public String getOpenRouterApiUrl() {
        return EnvUtil.getEnv("OPENROUTER_API_URL", "https://openrouter.ai/api/v1/chat/completions");
    }
}
