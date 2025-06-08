package ru.news.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты для NewsCategory
 */
class NewsCategoryTest {

    @Test
    void testCategorizeByContentAdvanced_Politics_ShouldReturnPolitics() {
        String title = "Путин встретился с министрами";
        String content = "Президент России провел совещание с правительством";

        NewsCategory result = NewsCategory.categorizeByContentAdvanced(title, content);

        assertEquals(NewsCategory.POLITICS, result);
    }

    @Test
    void testCategorizeByContentAdvanced_Economy_ShouldReturnEconomy() {
        String title = "Курс рубля укрепился";
        String content = "Российская валюта показала рост по отношению к доллару";

        NewsCategory result = NewsCategory.categorizeByContentAdvanced(title, content);

        assertEquals(NewsCategory.ECONOMY, result);
    }

    @Test
    void testCategorizeByContentAdvanced_NoKeywords_ShouldReturnOther() {
        String title = "Простая новость";
        String content = "Обычное содержание без специфических ключевых слов";

        NewsCategory result = NewsCategory.categorizeByContentAdvanced(title, content);

        assertEquals(NewsCategory.OTHER, result);
    }

    @Test
    void testGetDisplayName_ShouldReturnCorrectNames() {
        assertEquals("Политика", NewsCategory.POLITICS.getDisplayName());
        assertEquals("Экономика", NewsCategory.ECONOMY.getDisplayName());
        assertEquals("Спорт", NewsCategory.SPORT.getDisplayName());
        assertEquals("Технологии", NewsCategory.TECHNOLOGY.getDisplayName());
        assertEquals("Наука", NewsCategory.SCIENCE.getDisplayName());
        assertEquals("Культура", NewsCategory.CULTURE.getDisplayName());
        assertEquals("Здоровье", NewsCategory.HEALTH.getDisplayName());
        assertEquals("Общество", NewsCategory.SOCIETY.getDisplayName());
        assertEquals("Мир", NewsCategory.WORLD.getDisplayName());
        assertEquals("Другое", NewsCategory.OTHER.getDisplayName());
    }
}