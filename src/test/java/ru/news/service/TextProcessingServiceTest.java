package ru.news.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

/**
 * Тесты для сервиса обработки текста
 */
class TextProcessingServiceTest {

    private TextProcessingService textProcessingService;

    @BeforeEach
    void setUp() {
        textProcessingService = new TextProcessingService();
    }

    // Тесты извлечения ключевых слов
    @Test
    void extractKeywords_WithEmptyText_ShouldReturnEmptyList() {
        List<String> keywords = textProcessingService.extractKeywords("");

        assertNotNull(keywords);
        assertTrue(keywords.isEmpty());
    }

    @Test
    void extractKeywords_WithNullText_ShouldReturnEmptyList() {
        List<String> keywords = textProcessingService.extractKeywords(null);

        assertNotNull(keywords);
        assertTrue(keywords.isEmpty());
    }

    // Удалены устаревшие тесты extractPersons - функциональность перенесена в
    // AnalyticsService

    // Тесты создания резюме
    @Test
    void createSummary_WithValidText_ShouldReturnSummary() {
        String text = "Это первое предложение текста. Это второе предложение. Это третье предложение.";
        int maxLength = 50;

        String summary = textProcessingService.createSummary(text, maxLength);

        assertNotNull(summary);
        assertFalse(summary.isEmpty());
        assertTrue(summary.length() <= maxLength + 3); // +3 для "..."
    }

    @Test
    void createSummary_WithEmptyText_ShouldReturnEmpty() {
        String summary = textProcessingService.createSummary("", 100);

        assertNotNull(summary);
        assertTrue(summary.isEmpty());
    }

    @Test
    void createSummary_WithNullText_ShouldReturnEmpty() {
        String summary = textProcessingService.createSummary(null, 100);

        assertNotNull(summary);
        assertTrue(summary.isEmpty());
    }

    // Тесты очистки текста
    @Test
    void cleanText_WithHtmlTags_ShouldRemoveTags() {
        String text = "<p>Это <b>жирный</b> текст с <a href='#'>ссылкой</a>.</p>";

        String cleanText = textProcessingService.cleanText(text);

        assertNotNull(cleanText);
        assertFalse(cleanText.contains("<"));
        assertFalse(cleanText.contains(">"));
        assertTrue(cleanText.contains("Это"));
        assertTrue(cleanText.contains("жирный"));
    }

    @Test
    void cleanText_WithSpecialCharacters_ShouldRemoveSpecialChars() {
        String text = "Текст с @#$%^&*() специальными символами!";

        String cleanText = textProcessingService.cleanText(text);

        assertNotNull(cleanText);
        assertFalse(cleanText.contains("@"));
        assertFalse(cleanText.contains("#"));
        assertTrue(cleanText.contains("Текст"));
        assertTrue(cleanText.contains("специальными"));
    }

    @Test
    void cleanText_WithNullText_ShouldReturnEmpty() {
        String cleanText = textProcessingService.cleanText(null);

        assertNotNull(cleanText);
        assertTrue(cleanText.isEmpty());
    }

    // Тесты анализа тональности
    @Test
    void analyzeSentiment_WithPositiveText_ShouldReturnPositive() {
        String text = "Отличный успех! Хорошее достижение и положительный рост.";

        String sentiment = textProcessingService.analyzeSentiment(text);

        assertNotNull(sentiment);
        assertEquals("позитивная", sentiment);
    }

    @Test
    void analyzeSentiment_WithNegativeText_ShouldReturnNegative() {
        String text = "Ужасный провал! Плохая трагедия и отрицательный кризис.";

        String sentiment = textProcessingService.analyzeSentiment(text);

        assertNotNull(sentiment);
        assertEquals("негативная", sentiment);
    }

    @Test
    void analyzeSentiment_WithNeutralText_ShouldReturnNeutral() {
        String text = "Сегодня состоялась встреча представителей двух стран.";

        String sentiment = textProcessingService.analyzeSentiment(text);

        assertNotNull(sentiment);
        assertEquals("нейтральная", sentiment);
    }

    @Test
    void analyzeSentiment_WithEmptyText_ShouldReturnNeutral() {
        String sentiment = textProcessingService.analyzeSentiment("");

        assertNotNull(sentiment);
        assertEquals("нейтральная", sentiment);
    }

    @Test
    void analyzeSentiment_WithNullText_ShouldReturnNeutral() {
        String sentiment = textProcessingService.analyzeSentiment(null);

        assertNotNull(sentiment);
        assertEquals("нейтральная", sentiment);
    }
}
