package ru.news.parser;

import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;
import ru.news.exception.NewsParsingException;
import ru.news.model.News;
import ru.news.model.NewsCategory;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AbstractNewsParserTest {

    private static class TestNewsParser extends AbstractNewsParser {
        public TestNewsParser() {
            super("https://test.com");
        }
        
        @Override
        public String getSourceName() {
            return "Test Parser";
        }
        
        @Override
        protected List<News> parseNewsFromDocument(Document document) {
            // Простая реализация для тестирования
            List<News> newsList = new ArrayList<>();
            News news = new News();
            news.setTitle("Test News");
            news.setUrl("https://test.com/news");
            news.setSource(getSourceName());
            news.setCategory(NewsCategory.TECHNOLOGY);
            newsList.add(news);
            return newsList;
        }
        
        // Делаем методы видимыми для тестирования
        public String testNormalizeUrl(String url) {
            return normalizeUrl(url);
        }
        
        public String testNormalizeImageUrl(String url) {
            return normalizeImageUrl(url);
        }
        
        public News testCreateNewsObject(String title, String desc, String content, String url, String imageUrl) {
            return createNewsObject(title, desc, content, url, imageUrl);
        }
    }

    private TestNewsParser parser;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        parser = new TestNewsParser();
    }

    @Test
    void getSourceName_shouldReturnCorrectName() {
        assertEquals("Test Parser", parser.getSourceName());
    }
    
    @Test
    void getNewsUrl_shouldReturnBaseUrl() {
        assertEquals("https://test.com", parser.getNewsUrl());
    }
    
    @Test
    void normalizeUrl_shouldHandleVariousFormats() {
        // Абсолютные URL не должны изменяться
        assertEquals("https://example.com/news", parser.testNormalizeUrl("https://example.com/news"));
        
        // Относительные URL должны быть дополнены базовым URL
        assertEquals("https://test.com/news", parser.testNormalizeUrl("/news"));
        assertEquals("https://test.com/path", parser.testNormalizeUrl("path"));
        
        // Пустые или null URL
        assertEquals("", parser.testNormalizeUrl(""));
        assertEquals("", parser.testNormalizeUrl(null));
    }
    
    @Test
    void normalizeImageUrl_shouldHandleVariousFormats() {
        // Абсолютные URL
        assertEquals("https://example.com/image.jpg", parser.testNormalizeImageUrl("https://example.com/image.jpg"));
        
        // URL с протокол-независимым форматом
        assertEquals("https://example.com/image.jpg", parser.testNormalizeImageUrl("//example.com/image.jpg"));
        
        // Относительные URL
        assertEquals("https://test.com/image.jpg", parser.testNormalizeImageUrl("/image.jpg"));
        assertEquals("https://test.com/path/image.jpg", parser.testNormalizeImageUrl("path/image.jpg"));
        
        // Пустые или null URL
        assertNull(parser.testNormalizeImageUrl(""));
        assertNull(parser.testNormalizeImageUrl(null));
    }
    
    @Test
    void createNewsObject_shouldSetCorrectValues() {
        String title = "Test Title";
        String desc = "Test Description";
        String content = "Test Content";
        String url = "https://test.com/news";
        String imageUrl = "https://test.com/image.jpg";
        
        News news = parser.testCreateNewsObject(title, desc, content, url, imageUrl);
        
        assertNotNull(news);
        assertEquals(title, news.getTitle());
        assertEquals(desc, news.getDescription());
        assertEquals(content, news.getContent());
        assertEquals(url, news.getUrl());
        assertEquals(imageUrl, news.getImageUrl());
        assertEquals("Test Parser", news.getSource());
        assertNotNull(news.getPublishedAt());
    }
    
    @Test
    void parseNews_shouldReturnList() throws NewsParsingException {
        TestNewsParser spyParser = spy(parser);
        
        // Мокируем получение документа
        try {
            doReturn(mock(Document.class)).when(spyParser).getDocument(anyString());
            
            List<News> result = spyParser.parseNews();
            
            assertNotNull(result);
            assertFalse(result.isEmpty());
            assertEquals("Test News", result.get(0).getTitle());
            assertEquals(NewsCategory.TECHNOLOGY, result.get(0).getCategory());
        } catch (IOException e) {
            fail("Не должно быть исключения: " + e.getMessage());
        }
    }
    
    @Test
    void parseNews_shouldHandleIOException() throws NewsParsingException {
        TestNewsParser spyParser = spy(parser);
        
        try {
            // Мокируем ошибку получения документа
            doThrow(new IOException("Test exception")).when(spyParser).getDocument(anyString());
            
            // Вызов должен выбросить NewsParsingException
            assertThrows(NewsParsingException.class, spyParser::parseNews);
        } catch (IOException e) {
            fail("Не должно быть исключения здесь: " + e.getMessage());
        }
    }
} 