package ru.news.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Сервис для обработки текста и извлечения ключевых слов
 */
public class TextProcessingService {
    private static final Logger logger = LoggerFactory.getLogger(TextProcessingService.class);

    // Стоп-слова (наиболее частые слова, которые не несут смысловой нагрузки)
    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
            "и", "в", "во", "не", "что", "он", "на", "я", "с", "со", "как", "а", "то", "все", "она", "так",
            "его", "но", "да", "ты", "к", "у", "же", "вы", "за", "бы", "по", "только", "ее", "мне", "было",
            "вот", "от", "меня", "еще", "нет", "о", "из", "ему", "теперь", "когда", "даже", "ну", "вдруг",
            "ли", "если", "уже", "или", "ни", "быть", "был", "него", "до", "вас", "нибудь", "опять", "уж",
            "вам", "ведь", "там", "потом", "себя", "ничего", "ей", "может", "они", "тут", "где", "есть",
            "надо", "ней", "для", "мы", "тебя", "их", "чем", "была", "сам", "чтоб", "без", "будто", "чего",
            "раз", "тоже", "себе", "под", "будет", "ж", "тогда", "кто", "этот", "того", "потому", "этого",
            "какой", "совсем", "ним", "здесь", "этом", "один", "почти", "мой", "тем", "чтобы", "нее", "сейчас",
            "были", "куда", "зачем", "всех", "никогда", "можно", "при", "наконец", "два", "об", "другой",
            "хоть", "после", "над", "больше", "тот", "через", "эти", "нас", "про", "всего", "них", "какая",
            "много", "разве", "три", "эту", "моя", "впрочем", "хорошо", "свою", "этой", "перед", "иногда",
            "лучше", "чуть", "том", "нельзя", "такой", "им", "более", "всегда", "конечно", "всю", "между"));

    private static final Pattern WORD_PATTERN = Pattern.compile("[а-яё]+", Pattern.CASE_INSENSITIVE);
    private static final int MIN_WORD_LENGTH = 3;
    private static final int MAX_KEYWORDS = 10;
    
    // Паттерны для очистки текста
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");
    private static final Pattern SPECIAL_CHARS_PATTERN = Pattern.compile("[^а-яёА-ЯЁ0-9\\s.!?,-]");
    private static final Pattern MULTIPLE_SPACES_PATTERN = Pattern.compile("\\s+");
    
    // Кэши для улучшения производительности
    private final Map<String, List<String>> keywordsCache = new ConcurrentHashMap<>();
    private final Map<String, String> summaryCache = new ConcurrentHashMap<>();
    private final Map<String, String> sentimentCache = new ConcurrentHashMap<>();
    
    // Максимальный размер кэша
    private static final int MAX_CACHE_SIZE = 1000;
    
    // Слова для определения тональности текста
    private static final Set<String> POSITIVE_WORDS = new HashSet<>(Arrays.asList(
            "хорошо", "отлично", "успех", "победа", "достижение", "рост", "улучшение",
            "развитие", "прогресс", "положительный", "выгода", "польза", "радость"));

    private static final Set<String> NEGATIVE_WORDS = new HashSet<>(Arrays.asList(
            "плохо", "ужасно", "провал", "поражение", "кризис", "падение", "ухудшение",
            "проблема", "конфликт", "отрицательный", "ущерб", "вред", "трагедия", "катастрофа"));

    /**
     * Извлечение ключевых слов из текста
     */
    public List<String> extractKeywords(String text) {
        if (text == null || text.trim().isEmpty()) {
            return Collections.emptyList();
        }

        // Проверка кэша
        String cacheKey = generateCacheKey(text);
        if (keywordsCache.containsKey(cacheKey)) {
            return keywordsCache.get(cacheKey);
        }
        
        try {
            // Очистка и нормализация текста
            String cleanText = cleanText(text);

            // Разбиение на слова и подсчет частоты
            Map<String, Integer> wordFrequency = calculateWordFrequency(cleanText);

            // Сортировка по частоте и возврат топ-слов
            List<String> result = wordFrequency.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .limit(MAX_KEYWORDS)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
                    
            // Сохраняем в кэш, контролируя его размер
            manageCache(keywordsCache, cacheKey, result);
            
            return result;
        } catch (Exception e) {
            logger.error("Ошибка при извлечении ключевых слов: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }
    
    /**
     * Подсчет частоты слов в тексте
     */
    private Map<String, Integer> calculateWordFrequency(String cleanText) {
        Map<String, Integer> wordFrequency = new HashMap<>();

        Matcher matcher = WORD_PATTERN.matcher(cleanText.toLowerCase());
        while (matcher.find()) {
            String word = matcher.group();

            if (word.length() >= MIN_WORD_LENGTH && !STOP_WORDS.contains(word)) {
                wordFrequency.merge(word, 1, Integer::sum);
            }
        }
        
        return wordFrequency;
    }

    /**
     * Создание краткого резюме статьи
     */
    public String createSummary(String content, int maxLength) {
        if (content == null || content.trim().isEmpty()) {
            return "";
        }
        
        // Проверка разумных значений для maxLength
        maxLength = Math.max(50, Math.min(maxLength, 1000));
        
        // Проверка кэша
        String cacheKey = generateCacheKey(content + "_" + maxLength);
        if (summaryCache.containsKey(cacheKey)) {
            return summaryCache.get(cacheKey);
        }

        try {
            String cleanContent = cleanText(content);

            // Разбиение на предложения
            String[] sentences = cleanContent.split("[.!?]+");

            if (sentences.length == 0) {
                String result = truncateText(cleanContent, maxLength);
                manageCache(summaryCache, cacheKey, result);
                return result;
            }

            // Выбор наиболее информативных предложений
            List<String> selectedSentences = selectInformativeSentences(sentences, maxLength);

            if (selectedSentences.isEmpty()) {
                String result = truncateText(cleanContent, maxLength);
                manageCache(summaryCache, cacheKey, result);
                return result;
            }

            String result = String.join(". ", selectedSentences) + ".";
            manageCache(summaryCache, cacheKey, result);
            return result;
        } catch (Exception e) {
            logger.error("Ошибка при создании резюме: {}", e.getMessage(), e);
            return truncateText(content, maxLength);
        }
    }
    
    /**
     * Выбор информативных предложений для резюме
     */
    private List<String> selectInformativeSentences(String[] sentences, int maxLength) {
        List<String> selectedSentences = new ArrayList<>();
        int currentLength = 0;
        final int MIN_SENTENCE_LENGTH = 10;

        for (String sentence : sentences) {
            sentence = sentence.trim();
            if (sentence.length() > MIN_SENTENCE_LENGTH) { // Игнорируем слишком короткие предложения
                if (currentLength + sentence.length() + 2 <= maxLength) {
                    selectedSentences.add(sentence);
                    currentLength += sentence.length() + 2; // +2 для точки и пробела
                } else {
                    break;
                }
            }
        }
        
        return selectedSentences;
    }
    
    /**
     * Обрезка текста до заданной длины
     */
    private String truncateText(String text, int maxLength) {
        return text.length() > maxLength ? text.substring(0, maxLength) + "..." : text;
    }
    
    /**
     * Генерация ключа для кэша
     */
    private String generateCacheKey(String text) {
        // Используем первые 100 символов и хэш полного текста
        String prefix = text.length() > 100 ? text.substring(0, 100) : text;
        return prefix + "_" + text.hashCode();
    }
    
    /**
     * Управление размером кэша
     */
    private <T> void manageCache(Map<String, T> cache, String key, T value) {
        // Если кэш переполнен, удаляем случайный элемент
        if (cache.size() >= MAX_CACHE_SIZE) {
            String randomKey = cache.keySet().iterator().next();
            cache.remove(randomKey);
        }
        cache.put(key, value);
    }

    /**
     * Очистка текста от лишних символов и HTML тегов
     */
    public String cleanText(String text) {
        if (text == null) {
            return "";
        }

        return MULTIPLE_SPACES_PATTERN.matcher(
                SPECIAL_CHARS_PATTERN.matcher(
                    HTML_TAG_PATTERN.matcher(text).replaceAll(" ")
                ).replaceAll(" ")
            ).replaceAll(" ").trim();
    }

    /**
     * Определение тональности текста (упрощенная версия)
     */
    public String analyzeSentiment(String text) {
        if (text == null || text.trim().isEmpty()) {
            return "нейтральная";
        }
        
        // Проверка кэша
        String cacheKey = generateCacheKey(text);
        if (sentimentCache.containsKey(cacheKey)) {
            return sentimentCache.get(cacheKey);
        }

        try {
            String lowerText = text.toLowerCase();

            int positiveScore = countWordOccurrences(lowerText, POSITIVE_WORDS);
            int negativeScore = countWordOccurrences(lowerText, NEGATIVE_WORDS);

            String result;
            if (positiveScore > negativeScore) {
                result = "позитивная";
            } else if (negativeScore > positiveScore) {
                result = "негативная";
            } else {
                result = "нейтральная";
            }
            
            manageCache(sentimentCache, cacheKey, result);
            return result;
        } catch (Exception e) {
            logger.error("Ошибка при анализе тональности: {}", e.getMessage(), e);
            return "нейтральная";
        }
    }
    
    /**
     * Подсчет вхождений слов из набора в тексте
     */
    private int countWordOccurrences(String text, Set<String> words) {
        int count = 0;
        for (String word : words) {
            if (text.contains(word)) {
                count++;
            }
        }
        return count;
    }
    
    /**
     * Очистка кэшей для освобождения памяти
     */
    public void clearCaches() {
        keywordsCache.clear();
        summaryCache.clear();
        sentimentCache.clear();
        logger.info("Кэши TextProcessingService очищены");
    }
}
