package ru.news.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import ru.news.config.AppConfig;
import ru.news.model.News;
import ru.news.model.NewsCategory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiSummaryServiceDeepSeekTest {

    @Mock
    private AppConfig config;
    
    @Mock
    private NewsService newsService;
    
    private AiSummaryServiceDeepSeek aiService;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(config.getOpenRouterApiKey()).thenReturn("");
        aiService = new AiSummaryServiceDeepSeek(config, newsService);
    }
    
    @Test
    void generateTodayNewsSummary_noApiKey_returnsErrorMessage() {
        // Действие
        String result = aiService.generateTodayNewsSummary();
        
        // Проверка
        assertEquals("❌ OpenRouter API не настроен. Проверьте конфигурацию.", result);
        verify(config).getOpenRouterApiKey();
    }
    
    @Test
    void generateTodayNewsSummary_noNews_returnsEmptyMessage() {
        // Подготовка - создаем новый конфиг и сервис с API ключом
        AppConfig testConfig = mock(AppConfig.class);
        when(testConfig.getOpenRouterApiKey()).thenReturn("test-api-key");
        
        // Создаем новый экземпляр сервиса с тестовым конфигом
        AiSummaryServiceDeepSeek testAiService = new AiSummaryServiceDeepSeek(testConfig, newsService);
        
        when(newsService.getLatestNews(10)).thenReturn(new ArrayList<>());
        
        // Действие
        String result = testAiService.generateTodayNewsSummary();
        System.out.println("Today News Result: " + result);
        
        // Проверка
        assertEquals("📋 *Новостей в базе данных пока нет.*\n\nПопробуйте обновить новости командой /update", result);
        verify(newsService).getLatestNews(10);
    }
    
    @Test
    void generateTrendsAnalysis_noApiKey_returnsErrorMessage() {
        // Подготовка
        List<String> trends = List.of("Тренд 1", "Тренд 2");
        
        // Действие
        String result = aiService.generateTrendsAnalysis(trends);
        
        // Проверка
        assertEquals("❌ OpenRouter API не настроен. Проверьте конфигурацию.", result);
    }
    
    @Test
    void generateTrendsAnalysis_noTrends_returnsEmptyMessage() {
        // Подготовка - создаем новый конфиг и сервис с API ключом
        AppConfig testConfig = mock(AppConfig.class);
        when(testConfig.getOpenRouterApiKey()).thenReturn("test-api-key");
        
        // Создаем новый экземпляр сервиса с тестовым конфигом
        AiSummaryServiceDeepSeek testAiService = new AiSummaryServiceDeepSeek(testConfig, newsService);
        
        List<String> trends = new ArrayList<>();
        
        // Действие
        String result = testAiService.generateTrendsAnalysis(trends);
        System.out.println("Trends Analysis Result: " + result);
        
        // Проверка
        assertEquals("📈 *Анализ трендов:*\n\nТрендовые темы пока не найдены.", result);
    }
    
    @Test
    void generateCategorySummary_noApiKey_returnsErrorMessage() {
        // Подготовка
        String category = "Политика";
        
        // Действие
        String result = aiService.generateCategorySummary(category);
        
        // Проверка
        assertEquals("❌ OpenRouter API не настроен. Проверьте конфигурацию.", result);
    }
    
    @Test
    void generateCategorySummary_noNews_returnsEmptyMessage() {
        // Подготовка - создаем новый конфиг и сервис с API ключом
        AppConfig testConfig = mock(AppConfig.class);
        when(testConfig.getOpenRouterApiKey()).thenReturn("test-api-key");
        
        // Создаем новый экземпляр сервиса с тестовым конфигом
        AiSummaryServiceDeepSeek testAiService = new AiSummaryServiceDeepSeek(testConfig, newsService);
        
        String category = "Политика";
        when(newsService.getRecentNewsByCategory(category, 3)).thenReturn(new ArrayList<>());
        
        // Действие
        String result = testAiService.generateCategorySummary(category);
        System.out.println("Category Summary Result: " + result);
        
        // Проверка
        String expected = String.format("📂 *Сводка по категории \"%s\":*\n\nНовостей по данной категории за последние дни не найдено.", category);
        assertEquals(expected, result);
        verify(newsService).getRecentNewsByCategory(category, 3);
    }
    
    // Вспомогательный метод для создания тестовых новостей
    private List<News> createTestNews(int count) {
        List<News> newsList = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            News news = new News();
            news.setId((long)(i + 1));
            news.setTitle("Тестовый заголовок " + (i + 1));
            news.setContent("Тестовое содержание новости " + (i + 1) + ". Это тестовая новость для проверки работы AI сервиса.");
            news.setUrl("https://example.com/news/" + (i + 1));
            news.setPublishedAt(LocalDateTime.now().minusDays(i));
            
            news.setCategory(NewsCategory.POLITICS);
            newsList.add(news);
        }
        return newsList;
    }
} 