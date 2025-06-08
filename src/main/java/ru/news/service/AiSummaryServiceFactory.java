package ru.news.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.news.config.AppConfig;

/**
 * Фабрика для создания сервисов AI-сумморизации новостей
 */
public class AiSummaryServiceFactory {
    private static final Logger logger = LoggerFactory.getLogger(AiSummaryServiceFactory.class);
    
    private final AppConfig config;
    private final NewsService newsService;
    
    public AiSummaryServiceFactory(AppConfig config, NewsService newsService) {
        this.config = config;
        this.newsService = newsService;
    }
    
    /**
     * Создает и возвращает соответствующий сервис AI-сумморизации
     */
    public AiSummaryService createAiSummaryService() {
        // В данный момент мы используем DeepSeek через OpenRouter API
        logger.info("Создание AI сервиса с использованием DeepSeek");
        return new AiSummaryServiceDeepSeek(config, newsService);
    }
} 