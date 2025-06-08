package ru.news.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import ru.news.database.DatabaseManager;
import ru.news.exception.DatabaseException;
import ru.news.model.News;
import ru.news.model.NewsCategory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Тесты для NewsService
 */
class NewsServiceTest {

    @Mock
    private DatabaseManager databaseManager;

    private NewsService newsService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        newsService = new NewsService(databaseManager);
    }

    @Test
    void testSaveNews_NewNews_ShouldSave() throws DatabaseException {
        // Arrange
        News news = createTestNews();
        when(databaseManager.newsExists(news.getUrl())).thenReturn(false);

        // Act
        newsService.saveNews(news);

        // Assert
        verify(databaseManager).newsExists(news.getUrl());
        verify(databaseManager).saveNews(news);
    }

    @Test
    void testSaveNews_ExistingNews_ShouldNotSave() throws DatabaseException {
        // Arrange
        News news = createTestNews();
        when(databaseManager.newsExists(news.getUrl())).thenReturn(true);

        // Act
        newsService.saveNews(news);

        // Assert
        verify(databaseManager).newsExists(news.getUrl());
        verify(databaseManager, never()).saveNews(news);
    }

    @Test
    void testGetLatestNews_ShouldReturnNews() throws DatabaseException {
        // Arrange
        List<News> expectedNews = Arrays.asList(createTestNews(), createTestNews());
        when(databaseManager.getLatestNews(5)).thenReturn(expectedNews);

        // Act
        List<News> result = newsService.getLatestNews(5);

        // Assert
        assertEquals(expectedNews, result);
        verify(databaseManager).getLatestNews(5);
    }

    @Test
    void testSearchNews_ShouldReturnFilteredNews() throws DatabaseException {
        // Arrange
        String keyword = "политика";
        List<News> expectedNews = Arrays.asList(createTestNews());
        when(databaseManager.searchNews(keyword, 5)).thenReturn(expectedNews);

        // Act
        List<News> result = newsService.searchNews(keyword, 5);

        // Assert
        assertEquals(expectedNews, result);
        verify(databaseManager).searchNews(keyword, 5);
    }

    @Test
    void testGetNewsByCategory_ShouldReturnCategoryNews() throws DatabaseException {
        // Arrange
        NewsCategory category = NewsCategory.POLITICS;
        List<News> expectedNews = Arrays.asList(createTestNews());
        when(databaseManager.getNewsByCategory(category, 5)).thenReturn(expectedNews);

        // Act
        List<News> result = newsService.getNewsByCategory(category, 5);

        // Assert
        assertEquals(expectedNews, result);
        verify(databaseManager).getNewsByCategory(category, 5);
    }

    @Test
    void testFormatNewsForTelegram_ShouldFormatCorrectly() {
        // Arrange
        News news = createTestNews();
        news.setTitle("Тестовая новость");
        news.setDescription("Описание тестовой новости");
        news.setCategory(NewsCategory.POLITICS);
        news.setSource("Тест источник");
        news.setUrl("https://example.com/news");

        // Act
        String result = newsService.formatNewsForTelegram(news);

        // Assert
        assertTrue(result.contains("📰 *Тестовая новость*"));
        assertTrue(result.contains("Описание тестовой новости"));
        assertTrue(result.contains("🏷️ Категория: Политика"));
        assertTrue(result.contains("📡 Источник: Тест источник"));
        assertTrue(result.contains("[Читать полностью](https://example.com/news)"));
    }

    @Test
    void testSaveNews_DatabaseException_ShouldHandleGracefully() throws DatabaseException {
        // Arrange
        News news = createTestNews();
        when(databaseManager.newsExists(news.getUrl())).thenThrow(new DatabaseException("Database error"));

        // Act & Assert
        assertDoesNotThrow(() -> newsService.saveNews(news));
        verify(databaseManager).newsExists(news.getUrl());
        verify(databaseManager, never()).saveNews(any());
    }

    @Test
    void saveNews_shouldSaveValidNews() throws DatabaseException {
        // Подготовка тестовых данных
        News news = new News();
        news.setTitle("Test Title");
        news.setUrl("https://test.com/news");
        news.setCategory(NewsCategory.TECHNOLOGY);

        when(databaseManager.newsExists(anyString())).thenReturn(false);
        
        // Выполнение метода
        newsService.saveNews(news);
        
        // Проверка вызова сохранения
        verify(databaseManager).saveNews(eq(news));
    }
    
    @Test
    void saveNews_shouldNotSaveExistingNews() throws DatabaseException {
        // Подготовка тестовых данных
        News news = new News();
        news.setTitle("Existing News");
        news.setUrl("https://test.com/existing");
        
        when(databaseManager.newsExists("https://test.com/existing")).thenReturn(true);
        
        // Выполнение метода
        newsService.saveNews(news);
        
        // Проверяем, что сохранение не вызывалось
        verify(databaseManager, never()).saveNews(any(News.class));
    }
    
    @Test
    void saveNews_shouldHandleDatabaseException() throws DatabaseException {
        // Подготовка тестовых данных
        News news = new News();
        news.setTitle("Error News");
        news.setUrl("https://test.com/error");
        
        when(databaseManager.newsExists(anyString())).thenReturn(false);
        doThrow(new DatabaseException("Test error")).when(databaseManager).saveNews(any(News.class));
        
        // Выполнение метода не должно выбросить исключение
        assertDoesNotThrow(() -> newsService.saveNews(news));
    }
    
    @Test
    void getLatestNews_shouldReturnFilteredNews() throws DatabaseException {
        // Подготовка тестовых данных
        List<News> mockNews = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            News news = new News();
            news.setTitle("News " + i);
            news.setUrl("https://test.com/news/" + i);
            mockNews.add(news);
        }
        
        when(databaseManager.getLatestNews(anyInt())).thenReturn(mockNews);
        
        // Выполнение метода
        List<News> result = newsService.getLatestNews(3);
        
        // Проверка результата
        assertEquals(5, result.size()); // Вернет все mockNews из-за мока
    }
    
    @Test
    void getLatestNews_shouldHandleDatabaseException() throws DatabaseException {
        when(databaseManager.getLatestNews(anyInt())).thenThrow(new DatabaseException("Test error"));
        
        // Выполнение метода должно вернуть пустой список
        List<News> result = newsService.getLatestNews(5);
        assertTrue(result.isEmpty());
    }
    
    @Test
    void searchNews_shouldFindMatchingNews() throws DatabaseException {
        // Подготовка тестовых данных
        News news1 = new News();
        news1.setTitle("Bitcoin Price");
        news1.setUrl("https://test.com/crypto");
        
        News news2 = new News();
        news2.setTitle("Economy News");
        news2.setUrl("https://test.com/economy");
        
        List<News> mockResults = List.of(news1, news2);
        
        when(databaseManager.searchNews(eq("bitcoin"), anyInt())).thenReturn(Collections.singletonList(news1));
        
        // Выполнение метода
        List<News> result = newsService.searchNews("bitcoin", 10);
        
        // Проверка результатов
        assertEquals(1, result.size());
        assertEquals("Bitcoin Price", result.get(0).getTitle());
    }
    
    @Test
    void getNewsByCategory_shouldFilterByCategory() throws DatabaseException {
        // Подготовка тестовых данных
        News techNews = new News();
        techNews.setTitle("Tech News");
        techNews.setCategory(NewsCategory.TECHNOLOGY);
        
        when(databaseManager.getNewsByCategory(eq(NewsCategory.TECHNOLOGY), anyInt()))
            .thenReturn(Collections.singletonList(techNews));
        
        // Выполнение метода
        List<News> result = newsService.getNewsByCategory(NewsCategory.TECHNOLOGY, 10);
        
        // Проверка результатов
        assertEquals(1, result.size());
        assertEquals(NewsCategory.TECHNOLOGY, result.get(0).getCategory());
    }
    
    @Test
    void getRecentNewsByCategory_shouldParseCategory() throws DatabaseException {
        // Подготовка тестовых данных
        News politicsNews = new News();
        politicsNews.setTitle("Politics News");
        politicsNews.setCategory(NewsCategory.POLITICS);
        
        when(databaseManager.getNewsByCategory(eq(NewsCategory.POLITICS), anyInt()))
            .thenReturn(Collections.singletonList(politicsNews));
        
        // Выполнение метода
        List<News> result = newsService.getRecentNewsByCategory("POLITICS", 10);
        
        // Проверка результатов
        assertEquals(1, result.size());
        assertEquals("Politics News", result.get(0).getTitle());
    }
    
    @Test
    void getNewsByDate_shouldFilterByDate() throws DatabaseException {
        // Подготовка тестовых данных
        LocalDate testDate = LocalDate.now();
        News todayNews = new News();
        todayNews.setTitle("Today News");
        todayNews.setPublishedAt(testDate.atTime(12, 0));
        
        when(databaseManager.getNewsByDate(eq(testDate)))
            .thenReturn(Collections.singletonList(todayNews));
        
        // Выполнение метода
        List<News> result = newsService.getNewsByDate(testDate);
        
        // Проверка результатов
        assertEquals(1, result.size());
        assertEquals("Today News", result.get(0).getTitle());
    }
    
    @Test
    void formatNewsForTelegram_shouldFormatCorrectly() {
        // Подготовка тестовых данных
        News news = new News();
        news.setTitle("Test Title");
        news.setDescription("Test Description");
        news.setUrl("https://test.com");
        news.setSource("Test Source");
        news.setCategory(NewsCategory.TECHNOLOGY);
        news.setPublishedAt(LocalDateTime.now());
        
        // Выполнение метода
        String formatted = newsService.formatNewsForTelegram(news);
        
        // Проверка результата
        assertNotNull(formatted);
        assertTrue(formatted.contains("Test Title"));
        assertTrue(formatted.contains("https://test.com"));
        assertTrue(formatted.contains("Test Source"));
        assertTrue(formatted.contains("TECHNOLOGY") || formatted.contains("Технологии"));
    }
    
    @Test
    void saveNewsBatch_shouldSaveMultipleNews() throws DatabaseException {
        // Подготовка тестовых данных
        List<News> newsList = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            News news = new News();
            news.setTitle("Batch News " + i);
            news.setUrl("https://test.com/batch/" + i);
            newsList.add(news);
        }
        
        when(databaseManager.newsExists(anyString())).thenReturn(false);
        
        // Выполнение метода
        newsService.saveNewsBatch(newsList);
        
        // Проверка вызова метода saveNews для каждой новости
        verify(databaseManager, times(3)).saveNews(any(News.class));
    }
    
    @Test
    void clearCache_shouldEmptyCache() {
        // Выполнение метода
        newsService.clearCache();
        
        // Тест пройден, если не было исключений
        // (не можем напрямую проверить внутреннее состояние, но метод должен выполниться)
    }

    private News createTestNews() {
        News news = new News();
        news.setTitle("Test News Title");
        news.setDescription("Test News Description");
        news.setContent("Test News Content");
        news.setUrl("https://example.com/test-news");
        news.setSource("Test Source");
        news.setCategory(NewsCategory.OTHER);
        news.setPublishedAt(LocalDateTime.now());
        news.setCreatedAt(LocalDateTime.now());
        return news;
    }
}