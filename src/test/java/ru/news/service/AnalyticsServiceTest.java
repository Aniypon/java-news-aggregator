package ru.news.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import ru.news.database.DatabaseManager;
import ru.news.model.News;
import ru.news.model.NewsCategory;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AnalyticsServiceTest {

    @Mock
    private DatabaseManager databaseManager;

    private AnalyticsService analyticsService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        analyticsService = new AnalyticsService(databaseManager);
    }

    @Test
    void testGetCategoryStatistics() throws Exception {
        // Подготовка тестовых данных
        News news1 = new News(1, "Заголовок 1", "Описание 1", "http://test.com/1", NewsCategory.POLITICS,
                "Источник 1", LocalDateTime.now(), null);
        News news2 = new News(2, "Заголовок 2", "Описание 2", "http://test.com/2", NewsCategory.ECONOMY,
                "Источник 2", LocalDateTime.now(), null);
        News news3 = new News(3, "Заголовок 3", "Описание 3", "http://test.com/3", NewsCategory.POLITICS,
                "Источник 1", LocalDateTime.now(), null);

        List<News> mockNews = Arrays.asList(news1, news2, news3);
        when(databaseManager.getNewsSince(any(LocalDateTime.class))).thenReturn(mockNews);

        // Выполнение тестируемого метода
        Map<NewsCategory, Integer> result = analyticsService.getCategoryStatistics(7);

        // Проверка результатов
        assertNotNull(result);
        assertEquals(2, result.get(NewsCategory.POLITICS));
        assertEquals(1, result.get(NewsCategory.ECONOMY));

        // Проверка вызова мока
        verify(databaseManager).getNewsSince(any(LocalDateTime.class));
    }

    @Test
    void testGetTrendingTopics() throws Exception {
        // Подготовка тестовых данных
        News news1 = new News(1, "Президент обсудил экономику", "Важная встреча по экономике",
                "http://test.com/1", NewsCategory.POLITICS, "Источник 1", LocalDateTime.now(), null);
        News news2 = new News(2, "Экономика растет", "Рост экономики по всем показателям",
                "http://test.com/2", NewsCategory.ECONOMY, "Источник 2", LocalDateTime.now(), null);
        News news3 = new News(3, "Политический форум", "Международный политический форум",
                "http://test.com/3", NewsCategory.POLITICS, "Источник 1", LocalDateTime.now(), null);

        List<News> mockNews = Arrays.asList(news1, news2, news3);
        when(databaseManager.getNewsSince(any(LocalDateTime.class))).thenReturn(mockNews);

        // Выполнение тестируемого метода
        List<String> result = analyticsService.getTrendingTopics(7, 5);

        // Проверка результатов
        assertNotNull(result);
        assertFalse(result.isEmpty());

        // Проверка вызова мока
        verify(databaseManager).getNewsSince(any(LocalDateTime.class));
    }

    @Test
    void testGetCategoryStatisticsWithEmptyResult() throws Exception {
        // Подготовка пустого результата
        when(databaseManager.getNewsSince(any(LocalDateTime.class))).thenReturn(List.of());

        // Выполнение тестируемого метода
        Map<NewsCategory, Integer> result = analyticsService.getCategoryStatistics(7);

        // Проверка результатов
        assertNotNull(result);
        for (NewsCategory category : NewsCategory.values()) {
            assertEquals(0, result.get(category));
        }
    }
}
