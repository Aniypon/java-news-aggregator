package ru.news.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.news.database.DatabaseManager;
import ru.news.exception.DatabaseException;
import ru.news.model.News;
import ru.news.model.NewsCategory;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Сервис для работы с новостями
 */
public class NewsService {
    private static final Logger logger = LoggerFactory.getLogger(NewsService.class);
    private final DatabaseManager databaseManager;
    
    // Кэши для повышения производительности
    private final Map<String, List<News>> queryCache = new ConcurrentHashMap<>();
    private final Map<String, Long> queryCacheTimestamps = new ConcurrentHashMap<>();
    
    // Настройки кэширования
    private static final long CACHE_TTL_MS = 5 * 60 * 1000; // 5 минут
    private static final int MAX_CACHE_ENTRIES = 100;
    private static final int DEFAULT_NEWS_LIMIT = 20;

    public NewsService(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    /**
     * Сохранение новости
     */
    public void saveNews(News news) {
        if (news == null) {
            logger.warn("Попытка сохранения null-новости");
            return;
        }
        
        try {
            if (news.getUrl() == null || news.getUrl().trim().isEmpty()) {
                logger.warn("Попытка сохранения новости с пустым URL");
                return;
            }
            
            if (!databaseManager.newsExists(news.getUrl())) {
                databaseManager.saveNews(news);
                logger.info("Новость сохранена: {}", news.getTitle());
                
                // Инвалидируем кэш при добавлении новых новостей
                clearCache();
            } else {
                logger.debug("Новость уже существует: {}", news.getUrl());
            }
        } catch (DatabaseException e) {
            logger.error("Ошибка при сохранении новости: {}", e.getMessage(), e);
        }
    }

    /**
     * Получение последних новостей
     */
    public List<News> getLatestNews(int limit) {
        limit = validateLimit(limit);
        
        String cacheKey = "latest_" + limit;
        List<News> cachedResult = getCachedResult(cacheKey);
        if (cachedResult != null) {
            return cachedResult;
        }
        
        try {
            List<News> news = databaseManager.getLatestNews(limit);
            cacheResult(cacheKey, news);
            return news;
        } catch (DatabaseException e) {
            logger.error("Ошибка при получении последних новостей: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Поиск новостей по ключевому слову
     */
    public List<News> searchNews(String keyword, int limit) {
        if (keyword == null || keyword.trim().isEmpty()) {
            logger.warn("Попытка поиска с пустым ключевым словом");
            return Collections.emptyList();
        }
        
        limit = validateLimit(limit);
        String normalizedKeyword = keyword.toLowerCase().trim();
        String cacheKey = "search_" + normalizedKeyword + "_" + limit;
        
        List<News> cachedResult = getCachedResult(cacheKey);
        if (cachedResult != null) {
            return cachedResult;
        }
        
        try {
            List<News> results = databaseManager.searchNews(normalizedKeyword, limit);
            cacheResult(cacheKey, results);
            return results;
        } catch (DatabaseException e) {
            logger.error("Ошибка при поиске новостей: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Получение новостей по категории
     */
    public List<News> getNewsByCategory(NewsCategory category, int limit) {
        if (category == null) {
            logger.warn("Попытка получения новостей с null-категорией");
            return Collections.emptyList();
        }
        
        limit = validateLimit(limit);
        String cacheKey = "category_" + category.name() + "_" + limit;
        
        List<News> cachedResult = getCachedResult(cacheKey);
        if (cachedResult != null) {
            return cachedResult;
        }
        
        try {
            List<News> results = databaseManager.getNewsByCategory(category, limit);
            cacheResult(cacheKey, results);
            return results;
        } catch (DatabaseException e) {
            logger.error("Ошибка при получении новостей по категории: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Получение последних новостей по категории
     */
    public List<News> getRecentNewsByCategory(String categoryName, int limit) {
        if (categoryName == null || categoryName.trim().isEmpty()) {
            logger.warn("Попытка получения новостей с пустым именем категории");
            return Collections.emptyList();
        }
        
        // Конвертируем строку в NewsCategory
        NewsCategory category = parseCategoryName(categoryName);
        if (category == null) {
            return Collections.emptyList();
        }
        
        return getNewsByCategory(category, limit);
    }
    
    /**
     * Безопасное преобразование строки в NewsCategory
     */
    private NewsCategory parseCategoryName(String categoryName) {
        try {
            return NewsCategory.valueOf(categoryName.toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.warn("Неизвестная категория: {}", categoryName);
            return null;
        }
    }

    /**
     * Получение статистики по категориям
     */
    public List<String> getCategoriesStats() {
        String cacheKey = "category_stats";
        List<News> cachedResult = getCachedResult(cacheKey);
        
        if (cachedResult != null && !cachedResult.isEmpty()) {
            // В случае статистики мы храним результаты как List<News>, для совместимости с кэшем
            // а затем преобразуем их обратно в List<String>
            return convertToStringList(cachedResult);
        }
        
        try {
            List<String> stats = databaseManager.getCategoriesStats();
            // Сохраняем в кэш (конвертируя в List<News> для совместимости)
            cacheResult(cacheKey, convertFromStringList(stats));
            return stats;
        } catch (DatabaseException e) {
            logger.error("Ошибка при получении статистики: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }
    
    /**
     * Конвертация List<News> в List<String> (для статистики категорий)
     */
    private List<String> convertToStringList(List<News> newsList) {
        List<String> result = new ArrayList<>(newsList.size());
        for (News news : newsList) {
            if (news != null && news.getTitle() != null) {
                result.add(news.getTitle()); // используем title как хранилище для строки статистики
            }
        }
        return result;
    }
    
    /**
     * Конвертация List<String> в List<News> (для кэширования статистики категорий)
     */
    private List<News> convertFromStringList(List<String> stringList) {
        List<News> result = new ArrayList<>(stringList.size());
        for (String str : stringList) {
            if (str != null) {
                News dummyNews = new News();
                dummyNews.setTitle(str); // используем title как хранилище для строки статистики
                result.add(dummyNews);
            }
        }
        return result;
    }

    /**
     * Получение новостей по дате
     */
    public List<News> getNewsByDate(LocalDate date) {
        if (date == null) {
            logger.warn("Попытка получения новостей с null-датой");
            return Collections.emptyList();
        }
        
        String cacheKey = "date_" + date.toString();
        List<News> cachedResult = getCachedResult(cacheKey);
        
        if (cachedResult != null) {
            return cachedResult;
        }
        
        try {
            List<News> news = databaseManager.getNewsByDate(date);
            cacheResult(cacheKey, news);
            return news;
        } catch (DatabaseException e) {
            logger.error("Ошибка при получении новостей по дате: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Форматирование новости для отображения в Telegram
     */
    public String formatNewsForTelegram(News news) {
        if (news == null) {
            logger.warn("Попытка форматирования null-новости для Telegram");
            return "Новость не найдена";
        }
        
        StringBuilder message = new StringBuilder();

        message.append("📰 *").append(escapeMarkdown(news.getShortTitle())).append("*\n\n");

        if (news.getDescription() != null && !news.getDescription().isEmpty()) {
            message.append(escapeMarkdown(news.getShortDescription())).append("\n\n");
        }

        message.append("🏷️ Категория: ").append(
            news.getCategory() != null ? news.getCategory().getDisplayName() : "Неизвестно").append("\n");
        message.append("📡 Источник: ").append(escapeMarkdown(news.getSource())).append("\n");
        
        if (news.getUrl() != null && !news.getUrl().isEmpty()) {
            message.append("🔗 [Читать полностью](").append(news.getUrl()).append(")");
        }

        return message.toString();
    }

    /**
     * Экранирование символов для Markdown в Telegram
     */
    private String escapeMarkdown(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("*", "\\*")
                .replace("_", "\\_")
                .replace("[", "\\[")
                .replace("]", "\\]")
                .replace("(", "\\(")
                .replace(")", "\\)")
                .replace("`", "\\`");
    }
    
    /**
     * Получение результата из кэша
     */
    private List<News> getCachedResult(String cacheKey) {
        long now = System.currentTimeMillis();
        Long timestamp = queryCacheTimestamps.get(cacheKey);
        
        if (timestamp != null && now - timestamp < CACHE_TTL_MS) {
            List<News> cachedNews = queryCache.get(cacheKey);
            if (cachedNews != null) {
                logger.debug("Использование кэшированных результатов для ключа: {}", cacheKey);
                return new ArrayList<>(cachedNews); // создаем копию для безопасности
            }
        }
        
        return null;
    }
    
    /**
     * Сохранение результата в кэш
     */
    private void cacheResult(String cacheKey, List<News> news) {
        if (news == null) {
            return;
        }
        
        // Удаляем старые записи, если кэш переполнен
        if (queryCache.size() >= MAX_CACHE_ENTRIES) {
            String oldestKey = getOldestCacheKey();
            if (oldestKey != null) {
                queryCache.remove(oldestKey);
                queryCacheTimestamps.remove(oldestKey);
            }
        }
        
        queryCache.put(cacheKey, new ArrayList<>(news)); // сохраняем копию
        queryCacheTimestamps.put(cacheKey, System.currentTimeMillis());
    }
    
    /**
     * Получение самого старого ключа в кэше
     */
    private String getOldestCacheKey() {
        return queryCacheTimestamps.entrySet().stream()
                .min(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }
    
    /**
     * Очистка кэша
     */
    public void clearCache() {
        logger.debug("Очистка кэша новостей");
        queryCache.clear();
        queryCacheTimestamps.clear();
    }
    
    /**
     * Валидация лимита новостей
     */
    private int validateLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_NEWS_LIMIT;
        }
        return Math.min(limit, 100); // устанавливаем максимальное значение
    }
    
    /**
     * Пакетное добавление новостей
     */
    public void saveNewsBatch(List<News> newsList) {
        if (newsList == null || newsList.isEmpty()) {
            return;
        }
        
        int savedCount = 0;
        for (News news : newsList) {
            try {
                if (news != null && !databaseManager.newsExists(news.getUrl())) {
                    databaseManager.saveNews(news);
                    savedCount++;
                }
            } catch (DatabaseException e) {
                logger.error("Ошибка при пакетном сохранении новости: {}", e.getMessage(), e);
            }
        }
        
        if (savedCount > 0) {
            logger.info("Пакетное сохранение: добавлено {} из {} новостей", savedCount, newsList.size());
            clearCache(); // инвалидируем кэш
        }
    }
}