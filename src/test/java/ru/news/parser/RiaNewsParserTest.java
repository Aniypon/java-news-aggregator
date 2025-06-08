package ru.news.parser;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import ru.news.exception.NewsParsingException;
import ru.news.model.News;
import ru.news.model.NewsCategory;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class RiaNewsParserTest {

    private RiaNewsParser parser;

    @BeforeEach
    void setUp() {
        parser = new RiaNewsParser();
    }

    @Test
    void getSourceName_shouldReturnCorrectName() {
        assertEquals("РИА Новости", parser.getSourceName());
    }

    @Test
    void parseNews_shouldReturnEmptyList_whenConnectionFails() throws NewsParsingException {
        // Создаем стабы и моки для тестирования
        RiaNewsParser spyParser = spy(parser);
        
        // Мокируем getDocument чтобы он выбрасывал исключение
        try {
            doThrow(new IOException("Test connection error")).when(spyParser).getDocument(anyString());
            
            // Вызываем метод и проверяем, что он выбросит NewsParsingException
            assertThrows(NewsParsingException.class, () -> spyParser.parseNews());
        } catch (IOException e) {
            fail("Не должно быть исключения здесь: " + e.getMessage());
        }
    }

    @Test
    void parseNewsFromDocument_shouldHandleEmptyDocument() {
        // Создаем пустой мок Document
        Document document = mock(Document.class);
        
        // Устанавливаем возвращаемое значение для document.select() - пустой список
        when(document.select(anyString())).thenReturn(new Elements());
        
        // Вызываем тестируемый метод
        List<News> results = parser.parseNewsFromDocument(document);
        
        // Проверка результатов
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }
    
    @Test
    void getNewsUrl_shouldReturnCorrectUrl() {
        String url = parser.getNewsUrl();
        assertNotNull(url);
        assertTrue(url.contains("ria.ru"));
    }
} 