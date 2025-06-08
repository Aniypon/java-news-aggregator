package ru.news.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты для модели News
 */
class NewsTest {

    @Test
    void testNewsCreation_ShouldSetDefaultValues() {
        // Arrange & Act
        News news = new News("Title", "Description", "Content", "https://example.com", "Source");

        // Assert
        assertEquals("Title", news.getTitle());
        assertEquals("Description", news.getDescription());
        assertEquals("Content", news.getContent());
        assertEquals("https://example.com", news.getUrl());
        assertEquals("Source", news.getSource());
        assertNotNull(news.getPublishedAt());
        assertNotNull(news.getCreatedAt());
        assertNotNull(news.getCategory());
    }

    @Test
    void testGetShortTitle_LongTitle_ShouldTruncate() {
        // Arrange
        String longTitle = "This is a very long title that should be truncated because it exceeds the maximum length allowed for display purposes";
        News news = new News();
        news.setTitle(longTitle);

        // Act
        String shortTitle = news.getShortTitle();

        // Assert
        assertTrue(shortTitle.length() <= 100);
        assertTrue(shortTitle.endsWith("..."));
    }

    @Test
    void testGetShortTitle_ShortTitle_ShouldNotTruncate() {
        // Arrange
        String shortTitle = "Short title";
        News news = new News();
        news.setTitle(shortTitle);

        // Act
        String result = news.getShortTitle();

        // Assert
        assertEquals(shortTitle, result);
    }

    @Test
    void testGetShortDescription_LongDescription_ShouldTruncate() {
        // Arrange
        String longDescription = "This is a very long description that should be truncated because it exceeds the maximum length allowed for display purposes in the news aggregator application";
        News news = new News();
        news.setDescription(longDescription);

        // Act
        String shortDescription = news.getShortDescription();

        // Assert
        assertTrue(shortDescription.length() <= 200);
        assertTrue(shortDescription.endsWith("..."));
    }

    @Test
    void testEquals_SameUrl_ShouldBeEqual() {
        // Arrange
        News news1 = new News();
        news1.setUrl("https://example.com/news");

        News news2 = new News();
        news2.setUrl("https://example.com/news");

        // Act & Assert
        assertEquals(news1, news2);
        assertEquals(news1.hashCode(), news2.hashCode());
    }

    @Test
    void testEquals_DifferentUrl_ShouldNotBeEqual() {
        // Arrange
        News news1 = new News();
        news1.setUrl("https://example.com/news1");

        News news2 = new News();
        news2.setUrl("https://example.com/news2");

        // Act & Assert
        assertNotEquals(news1, news2);
    }

    @Test
    void testToString_ShouldContainMainFields() {
        // Arrange
        News news = new News();
        news.setId(1L);
        news.setTitle("Test Title");
        news.setSource("Test Source");
        news.setCategory(NewsCategory.POLITICS);
        news.setPublishedAt(LocalDateTime.now());

        // Act
        String result = news.toString();

        // Assert
        assertTrue(result.contains("id=1"));
        assertTrue(result.contains("title='Test Title'"));
        assertTrue(result.contains("source='Test Source'"));
        assertTrue(result.contains("category=POLITICS"));
    }
}