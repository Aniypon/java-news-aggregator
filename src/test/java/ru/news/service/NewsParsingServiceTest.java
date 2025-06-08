package ru.news.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import ru.news.exception.NewsParsingException;
import ru.news.model.News;
import ru.news.parser.NewsParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NewsParsingServiceTest {

    @Mock
    private NewsService newsService;
    
    @Mock
    private NewsParser mockParser1;
    
    @Mock
    private NewsParser mockParser2;
    
    private NewsParsingService parsingService;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Настраиваем парсеры
        when(mockParser1.getSourceName()).thenReturn("Mock Parser 1");
        when(mockParser2.getSourceName()).thenReturn("Mock Parser 2");
        
        // Внедряем тестовый список парсеров через рефлексию
        parsingService = new NewsParsingService(newsService);
        
        try {
            java.lang.reflect.Field parsersField = NewsParsingService.class.getDeclaredField("parsers");
            parsersField.setAccessible(true);
            List<NewsParser> parsers = new ArrayList<>();
            parsers.add(mockParser1);
            parsers.add(mockParser2);
            parsersField.set(parsingService, parsers);
        } catch (Exception e) {
            fail("Не удалось настроить тестовый список парсеров: " + e.getMessage());
        }
    }
    
    @Test
    void parseAllSources_shouldParseFromAllSources() throws NewsParsingException {
        // Подготавливаем тестовые данные
        News news1 = new News();
        news1.setTitle("News from Parser 1");
        
        News news2 = new News();
        news2.setTitle("News from Parser 2");
        
        when(mockParser1.parseNews()).thenReturn(Collections.singletonList(news1));
        when(mockParser2.parseNews()).thenReturn(Collections.singletonList(news2));
        
        // Вызываем тестируемый метод
        parsingService.parseAllSources();
        
        // Проверяем, что все парсеры были вызваны
        verify(mockParser1).parseNews();
        verify(mockParser2).parseNews();
        
        // Проверяем, что новости были сохранены
        verify(newsService, times(2)).saveNews(any(News.class));
    }
    
    @Test
    void parseAllSources_shouldHandleExceptions() throws NewsParsingException {
        // Настраиваем первый парсер для выброса исключения
        when(mockParser1.parseNews()).thenThrow(new NewsParsingException("Test exception"));
        
        // Настраиваем второй парсер для успешного парсинга
        News news = new News();
        news.setTitle("News from Parser 2");
        when(mockParser2.parseNews()).thenReturn(Collections.singletonList(news));
        
        // Вызываем тестируемый метод
        // Проверяем, что исключение не выбрасывается и работа продолжается со вторым парсером
        assertDoesNotThrow(() -> parsingService.parseAllSources());
        
        // Проверяем, что несмотря на ошибку в первом парсере, второй парсер был вызван
        verify(mockParser2).parseNews();
        
        // И новости от второго парсера были сохранены
        verify(newsService).saveNews(any(News.class));
    }
    
    @Test
    void parseAllSources_shouldHandleEmptyResults() throws NewsParsingException {
        // Настраиваем оба парсера для возврата пустых списков
        when(mockParser1.parseNews()).thenReturn(Collections.emptyList());
        when(mockParser2.parseNews()).thenReturn(Collections.emptyList());
        
        // Вызываем тестируемый метод
        parsingService.parseAllSources();
        
        // Проверяем, что оба парсера были вызваны
        verify(mockParser1).parseNews();
        verify(mockParser2).parseNews();
        
        // Но сохранение новостей не вызывалось
        verify(newsService, never()).saveNews(any(News.class));
    }
    
    @Test
    void parseAllSources_shouldHandleNullResults() throws NewsParsingException {
        // Настраиваем парсер для возврата null
        when(mockParser1.parseNews()).thenReturn(null);
        when(mockParser2.parseNews()).thenReturn(Collections.emptyList());
        
        // Вызываем тестируемый метод
        parsingService.parseAllSources();
        
        // Проверяем, что оба парсера были вызваны
        verify(mockParser1).parseNews();
        verify(mockParser2).parseNews();
        
        // Но сохранение новостей не вызывалось
        verify(newsService, never()).saveNews(any(News.class));
    }
    
    @Test
    void getAvailableSources_shouldReturnSourceNames() {
        // Вызываем тестируемый метод
        List<String> sources = parsingService.getAvailableSources();
        
        // Проверяем результат
        assertNotNull(sources);
        assertEquals(2, sources.size());
        assertTrue(sources.contains("Mock Parser 1"));
        assertTrue(sources.contains("Mock Parser 2"));
    }
    
    @Test
    void shutdown_shouldShutdownExecutorService() {
        // Вызываем тестируемый метод
        parsingService.shutdown();
        
        // Проверяем, что сервис остановлен
        assertFalse(parsingService.isRunning());
    }
    
    @Test
    void isRunning_shouldReturnCorrectState() {
        // По умолчанию сервис должен быть запущен
        assertTrue(parsingService.isRunning());
        
        // После остановки должен быть остановлен
        parsingService.shutdown();
        assertFalse(parsingService.isRunning());
    }
} 