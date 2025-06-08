package ru.news.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.news.database.DatabaseManager;
import ru.news.exception.DatabaseException;
import ru.news.model.News;
import ru.news.model.NewsCategory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Сервис для аналитики и статистики новостей
 */
public class AnalyticsService {
    private static final Logger logger = LoggerFactory.getLogger(AnalyticsService.class);

    private final DatabaseManager databaseManager;
    private final TextProcessingService textProcessingService;
    
    // Кэширование форматтеров для улучшения производительности
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DISPLAY_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    // Список основных имен/ключевых слов для анализа
    private static final Set<String> CORE_PERSON_KEYWORDS = Set.of(
            "путин", "медведев", "лавров", "мишустин", "силуанов",
            "трамп", "байден", "зеленский", "си", "эрдоган");
    
    // Параметры по умолчанию
    private static final int DEFAULT_DAYS_BACK = 7;
    private static final int DEFAULT_LIMIT = 15;
    private static final int MAX_KEYWORDS_PER_NEWS = 10;

    public AnalyticsService(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
        this.textProcessingService = new TextProcessingService();
    }

    /**
     * Получение статистики по категориям за период
     */
    public Map<NewsCategory, Integer> getCategoryStatistics(int daysBack) {
        try {
            // Инициализируем все категории нулями
            Map<NewsCategory, Integer> stats = initializeCategoriesMap();
            
            // Получаем новости за указанный период
            List<News> recentNews = getNewsForPeriod(daysBack);
            
            // Подсчитываем по категориям
            for (News news : recentNews) {
                stats.merge(news.getCategory(), 1, Integer::sum);
            }

            return stats;
        } catch (DatabaseException e) {
            logger.error("Ошибка при получении статистики по категориям: {}", e.getMessage(), e);
            return Collections.emptyMap();
        }
    }

    /**
     * Инициализирует карту категорий с нулевыми значениями
     */
    private Map<NewsCategory, Integer> initializeCategoriesMap() {
        Map<NewsCategory, Integer> stats = new HashMap<>();
        for (NewsCategory category : NewsCategory.values()) {
            stats.put(category, 0);
        }
        return stats;
    }

    /**
     * Получение новостей за указанный период
     */
    private List<News> getNewsForPeriod(int daysBack) throws DatabaseException {
        return databaseManager.getNewsSince(LocalDateTime.now().minusDays(daysBack));
    }

    /**
     * Определение трендовых тем за последние 7 дней
     */
    public List<String> getTrendingTopics(int limit) {
        return getTrendingTopics(DEFAULT_DAYS_BACK, limit);
    }

    /**
     * Определение трендовых тем за период
     */
    public List<String> getTrendingTopics(int daysBack, int limit) {
        try {
            List<News> newsList = getNewsForPeriod(daysBack);
            Map<String, Integer> keywordCounts = extractAndCountKeywords(newsList);

            return getSortedTopEntries(keywordCounts, limit);
        } catch (DatabaseException e) {
            logger.error("Ошибка при определении трендовых тем: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Извлечение и подсчет ключевых слов из списка новостей
     */
    private Map<String, Integer> extractAndCountKeywords(List<News> newsList) {
        Map<String, Integer> keywordCounts = new HashMap<>();
        
        for (News news : newsList) {
            String textToAnalyze = combineNewsText(news);
            List<String> keywords = textProcessingService.extractKeywords(textToAnalyze);
            
            if (keywords.size() > MAX_KEYWORDS_PER_NEWS) {
                keywords = keywords.subList(0, MAX_KEYWORDS_PER_NEWS);
            }
            
            for (String keyword : keywords) {
                keywordCounts.merge(keyword, 1, Integer::sum);
            }
        }
        
        return keywordCounts;
    }

    /**
     * Объединяет заголовок и содержимое новости для анализа текста
     */
    private String combineNewsText(News news) {
        return news.getTitle() + " " + (news.getContent() != null ? news.getContent() : "");
    }

    /**
     * Возвращает отсортированный список топ-entries из карты по значению
     */
    private <T> List<T> getSortedTopEntries(Map<T, Integer> countMap, int limit) {
        return countMap.entrySet().stream()
                .sorted(Map.Entry.<T, Integer>comparingByValue().reversed())
                .limit(limit)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    /**
     * Анализ упоминаний персон за последние 7 дней
     */
    public Map<String, Integer> analyzePersonMentions() {
        return analyzePersonMentions(DEFAULT_DAYS_BACK, DEFAULT_LIMIT);
    }

    /**
     * Анализ упоминаний персон за указанный период
     */
    public Map<String, Integer> analyzePersonMentions(int daysBack, int limit) {
        try {
            List<News> newsList = getNewsForPeriod(daysBack);
            Map<String, Integer> personCounts = new HashMap<>();

            for (News news : newsList) {
                String textToAnalyze = combineNewsText(news);
                List<String> potentialEntities = textProcessingService.extractKeywords(textToAnalyze);

                for (String entity : potentialEntities) {
                    if (isValidCorePerson(entity)) {
                        personCounts.merge(entity, 1, Integer::sum);
                    }
                }
            }

            return personCounts.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .limit(limit)
                    .collect(Collectors.toMap(
                        Map.Entry::getKey, 
                        Map.Entry::getValue, 
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                    ));
        } catch (DatabaseException e) {
            logger.error("Ошибка при анализе упоминаний персон: {}", e.getMessage(), e);
            return Collections.emptyMap();
        }
    }

    /**
     * Получение упоминаний персон (алиас для analyzePersonMentions)
     */
    public Map<String, Integer> getPersonMentions(int limit) {
        return analyzePersonMentions(DEFAULT_DAYS_BACK, limit);
    }

    /**
     * Проверяет, является ли извлеченная сущность основной персоной
     */
    private boolean isValidCorePerson(String entityName) {
        if (entityName == null || entityName.trim().isEmpty()) {
            return false;
        }
        String normalizedName = entityName.toLowerCase().trim();
        return CORE_PERSON_KEYWORDS.contains(normalizedName);
    }

    /**
     * Форматирование статистики по персонам для Telegram
     */
    public String formatPersonStatisticsForTelegram(Map<String, Integer> personStats) {
        if (personStats == null || personStats.isEmpty()) {
            return "👤 *Упоминаемые персоны:*\n\nВ последних новостях не найдено упоминаний отслеживаемых персон.";
        }

        StringBuilder result = new StringBuilder();
        result.append("👤 *Упоминаемые персоны:*\n");

        for (Map.Entry<String, Integer> entry : personStats.entrySet()) {
            result.append("• ").append(entry.getKey()).append("\n");
        }

        result.append(
                "\n💡 *Подсказка:* Используйте `/search имя_персоны` для поиска новостей с упоминанием конкретной персоны.");

        return result.toString();
    }

    /**
     * Поиск наиболее упоминаемых персон
     */
    public List<String> getMostMentionedPersons(int daysBack, int limit) {
        try {
            Map<String, Integer> mentionedCorePersons = analyzePersonMentions(daysBack, limit);
            return new ArrayList<>(mentionedCorePersons.keySet());
        } catch (Exception e) {
            logger.error("Ошибка при получении наиболее упоминаемых персон: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Анализ динамики появления новостей по ключевому слову
     */
    public Map<String, Integer> getKeywordDynamics(String keyword, int daysBack) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return Collections.emptyMap();
        }
        
        try {
            Map<String, Integer> dynamics = new LinkedHashMap<>();
            String normalizedKeyword = keyword.toLowerCase();
            
            for (int i = daysBack; i >= 0; i--) {
                LocalDateTime date = LocalDateTime.now().minusDays(i);
                String dateStr = date.format(DATE_FORMATTER);

                List<News> dayNews = databaseManager.getNewsForDate(date);
                int count = countKeywordOccurrences(dayNews, normalizedKeyword);
                dynamics.put(dateStr, count);
            }

            return dynamics;

        } catch (DatabaseException e) {
            logger.error("Ошибка при анализе динамики ключевого слова: {}", e.getMessage(), e);
            return Collections.emptyMap();
        }
    }
    
    /**
     * Подсчет вхождений ключевого слова в списке новостей
     */
    private int countKeywordOccurrences(List<News> newsList, String keyword) {
        int count = 0;
        for (News news : newsList) {
            String text = (news.getTitle() + " " + news.getDescription() + " " +
                    (news.getContent() != null ? news.getContent() : "")).toLowerCase();
            if (text.contains(keyword)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Анализ тональности новостей по категориям
     */
    public Map<NewsCategory, Map<String, Integer>> getSentimentAnalysis(int daysBack) {
        try {
            Map<NewsCategory, Map<String, Integer>> sentimentStats = initializeSentimentStatsMap();
            List<News> recentNews = getNewsForPeriod(daysBack);

            for (News news : recentNews) {
                String sentiment = textProcessingService.analyzeSentiment(
                        news.getTitle() + " " + news.getDescription());

                Map<String, Integer> categorySentiments = sentimentStats.get(news.getCategory());
                if (categorySentiments != null) {
                    categorySentiments.merge(sentiment, 1, Integer::sum);
                }
            }

            return sentimentStats;

        } catch (DatabaseException e) {
            logger.error("Ошибка при анализе тональности: {}", e.getMessage(), e);
            return Collections.emptyMap();
        }
    }
    
    /**
     * Инициализация структуры данных для статистики тональности
     */
    private Map<NewsCategory, Map<String, Integer>> initializeSentimentStatsMap() {
        Map<NewsCategory, Map<String, Integer>> sentimentStats = new HashMap<>();
        
        for (NewsCategory category : NewsCategory.values()) {
            Map<String, Integer> sentiments = new HashMap<>();
            sentiments.put("позитивная", 0);
            sentiments.put("негативная", 0);
            sentiments.put("нейтральная", 0);
            sentimentStats.put(category, sentiments);
        }
        
        return sentimentStats;
    }

    /**
     * Форматирование статистики для отображения в Telegram
     */
    public String formatStatisticsForTelegram(Map<NewsCategory, Integer> stats) {
        if (stats == null || stats.isEmpty()) {
            return "📊 *Статистика по категориям:*\n\nНет данных для отображения.";
        }
        
        StringBuilder result = new StringBuilder();
        result.append("📊 *Статистика по категориям:*\n\n");

        List<Map.Entry<NewsCategory, Integer>> sorted = stats.entrySet().stream()
                .sorted(Map.Entry.<NewsCategory, Integer>comparingByValue().reversed())
                .collect(Collectors.toList());

        for (Map.Entry<NewsCategory, Integer> entry : sorted) {
            NewsCategory category = entry.getKey();
            Integer count = entry.getValue();

            result.append("📊 ")
                    .append(category.getDisplayName())
                    .append(": ")
                    .append(count)
                    .append(" новост")
                    .append(getWordEndingForCount(count))
                    .append("\n");
        }

        return result.toString();
    }
    
    /**
     * Получение правильного окончания слова "новость" в зависимости от количества
     */
    private String getWordEndingForCount(int count) {
        if (count % 10 == 1 && count % 100 != 11) {
            return "ь";
        } else if (count % 10 >= 2 && count % 10 <= 4 && (count % 100 < 10 || count % 100 >= 20)) {
            return "и";
        } else {
            return "ей";
        }
    }

    /**
     * Форматирование трендовых тем для Telegram
     */
    public String formatTrendingTopicsForTelegram(List<String> topics) {
        if (topics == null || topics.isEmpty()) {
            return "🔥 *Трендовые темы:*\n\nНет данных для отображения.";
        }
        
        StringBuilder result = new StringBuilder();
        result.append("🔥 *Трендовые темы:*\n\n");

        for (int i = 0; i < topics.size(); i++) {
            result.append(i + 1)
                    .append(". ")
                    .append(topics.get(i))
                    .append("\n");
        }

        return result.toString();
    }
    
    /**
     * Предикат для фильтрации дубликатов в стримах по заданному ключу
     */
    public static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
        Set<Object> seen = ConcurrentHashMap.newKeySet();
        return t -> seen.add(keyExtractor.apply(t));
    }
}
