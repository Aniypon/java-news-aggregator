package ru.news.service;

import java.util.List;

/**
 * Интерфейс для сервисов AI-сумморизации новостей
 */
public interface AiSummaryService {
    
    /**
     * Создает краткое изложение последних новостей
     */
    String generateTodayNewsSummary();
    
    /**
     * Создает анализ трендов новостей
     */
    String generateTrendsAnalysis(List<String> trends);
    
    /**
     * Создает персонализированную сводку по категории
     */
    String generateCategorySummary(String categoryName);
} 