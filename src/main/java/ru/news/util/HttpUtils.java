package ru.news.util;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.news.exception.NewsParsingException;

import java.io.IOException;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Утилитарный класс для HTTP-операций
 * Содержит общие методы для выполнения HTTP-запросов с поддержкой повторных попыток
 */
public class HttpUtils {
    private static final Logger logger = LoggerFactory.getLogger(HttpUtils.class);
    
    // Константы для повторных попыток
    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final int DEFAULT_BASE_DELAY_MS = 2000;
    private static final int DEFAULT_TIMEOUT_MS = 15000;
    
    // Список разных User-Agent для минимизации блокировок
    private static final String[] USER_AGENTS = new String[] {
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:121.0) Gecko/20100101 Firefox/121.0",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:121.0) Gecko/20100101 Firefox/121.0"
    };
    
    // Псевдо-случайный генератор для выбора User-Agent
    private static final Random random = new Random();
    
    /**
     * Возвращает случайный User-Agent из списка
     * @return строка User-Agent
     */
    public static String getRandomUserAgent() {
        return USER_AGENTS[random.nextInt(USER_AGENTS.length)];
    }
    
    /**
     * Загружает HTML-документ с поддержкой повторных попыток при ошибках 429 (Too Many Requests)
     * 
     * @param url URL для загрузки
     * @return объект Document от Jsoup
     * @throws NewsParsingException при ошибке загрузки
     */
    public static Document fetchDocumentWithRetry(String url) throws NewsParsingException {
        return fetchDocumentWithRetry(url, DEFAULT_MAX_RETRIES, DEFAULT_BASE_DELAY_MS, DEFAULT_TIMEOUT_MS);
    }
    
    /**
     * Загружает HTML-документ с настраиваемыми параметрами повторных попыток
     * 
     * @param url URL для загрузки
     * @param maxRetries максимальное количество попыток
     * @param baseDelayMs базовая задержка между попытками в миллисекундах
     * @param timeoutMs таймаут соединения в миллисекундах
     * @return объект Document от Jsoup
     * @throws NewsParsingException при ошибке загрузки
     */
    public static Document fetchDocumentWithRetry(String url, int maxRetries, int baseDelayMs, int timeoutMs) 
            throws NewsParsingException {
        int retryCount = 0;
        
        while (true) {
            try {
                // Используем разные User-Agent при каждой попытке
                String userAgent = getRandomUserAgent();
                
                logger.debug("Загружаем URL: {} с User-Agent: {}", url, userAgent);
                return Jsoup.connect(url)
                        .userAgent(userAgent)
                        .timeout(timeoutMs)
                        .followRedirects(true)
                        .get();
                        
            } catch (IOException e) {
                // Проверяем, является ли это ошибкой HTTP 429 (Too Many Requests)
                if (e.getMessage() != null && e.getMessage().contains("Status=429")) {
                    retryCount++;
                    
                    // Если превышено максимальное количество попыток, выбрасываем исключение
                    if (retryCount > maxRetries) {
                        logger.error("Ошибка при загрузке URL {}: {}. Превышено макс. число попыток.", 
                                url, e.getMessage());
                        throw new NewsParsingException(
                                "Не удалось загрузить документ после " + maxRetries + " попыток: " + url, e);
                    }
                    
                    // Вычисляем время задержки с экспоненциальным ростом (2, 4, 8 секунд и т.д.)
                    long delayMs = baseDelayMs * (long)Math.pow(2, retryCount - 1);
                    // Добавляем небольшую случайность (джиттер) для предотвращения синхронизированных повторных попыток
                    delayMs += (long)(Math.random() * baseDelayMs * 0.5);
                    
                    logger.warn("Получен HTTP 429 для URL: {}. Повторная попытка {} из {} через {} мс", 
                            url, retryCount, maxRetries, delayMs);
                    
                    try {
                        TimeUnit.MILLISECONDS.sleep(delayMs);
                        continue; // Повторяем попытку
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new NewsParsingException("Прервано ожидание повторной попытки: " + url, ie);
                    }
                } else {
                    // Другая IO ошибка, не связанная с 429
                    logger.error("Ошибка при загрузке URL {}: {}", url, e.getMessage());
                    throw new NewsParsingException("Не удалось загрузить документ: " + url, e);
                }
            }
        }
    }
} 