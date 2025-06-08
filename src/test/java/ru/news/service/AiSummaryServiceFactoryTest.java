package ru.news.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import ru.news.config.AppConfig;

import static org.junit.jupiter.api.Assertions.*;

class AiSummaryServiceFactoryTest {

    @Mock
    private AppConfig config;
    
    @Mock
    private NewsService newsService;
    
    private AiSummaryServiceFactory factory;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        factory = new AiSummaryServiceFactory(config, newsService);
    }
    
    @Test
    void createAiSummaryService_shouldReturnDeepSeekService() {
        // Действие
        AiSummaryService service = factory.createAiSummaryService();
        
        // Проверка
        assertNotNull(service);
        assertTrue(service instanceof AiSummaryServiceDeepSeek);
    }
} 