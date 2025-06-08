package ru.news.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Модель новости
 */
public class News {
    private Long id;
    private String title;
    private String description;
    private String content;
    private String url;
    private String source;
    private String imageUrl;
    private NewsCategory category;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
    
    private static final int MAX_SHORT_LENGTH = 100;
    private static final int SHORT_LENGTH_CUTOFF = 97;

    public News() {
        initializeDates();
    }

    public News(String title, String description, String content, String url, String source) {
        this();
        this.title = title;
        this.description = description;
        this.content = content;
        this.url = url;
        this.source = source;
        // Используем улучшенную категоризацию
        categorize();
    }

    // Конструктор для тестов с полным набором параметров
    public News(long id, String title, String description, String url, NewsCategory category,
            String source, LocalDateTime publishedAt, String imageUrl) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.url = url;
        this.category = category;
        this.source = source;
        this.publishedAt = publishedAt != null ? publishedAt : LocalDateTime.now();
        this.imageUrl = imageUrl;
        this.createdAt = LocalDateTime.now();
    }

    /**
     * Инициализирует временные поля
     */
    private void initializeDates() {
        this.publishedAt = LocalDateTime.now();
        this.createdAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public NewsCategory getCategory() {
        return category;
    }

    public void setCategory(NewsCategory category) {
        this.category = category;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(LocalDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Получает краткое описание новости для отображения
     */
    public String getShortDescription() {
        return getShortenedText(description);
    }

    /**
     * Получает краткий заголовок для отображения
     */
    public String getShortTitle() {
        return getShortenedText(title);
    }

    /**
     * Обобщенный метод для сокращения текста
     */
    private String getShortenedText(String text) {
        if (text != null && text.length() > MAX_SHORT_LENGTH) {
            return text.substring(0, SHORT_LENGTH_CUTOFF) + "...";
        }
        return text;
    }

    /**
     * Пересчитывает категорию новости на основе улучшенного алгоритма
     */
    public void recategorize() {
        categorize();
    }
    
    /**
     * Категоризирует новость на основе заголовка и содержания
     */
    private void categorize() {
        StringBuilder contentBuilder = new StringBuilder();
        if (this.description != null) {
            contentBuilder.append(this.description).append(" ");
        }
        if (this.content != null) {
            contentBuilder.append(this.content);
        }
        this.category = NewsCategory.categorizeByContentAdvanced(this.title, contentBuilder.toString());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        News news = (News) o;
        return Objects.equals(url, news.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url);
    }

    @Override
    public String toString() {
        return "News{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", source='" + source + '\'' +
                ", category=" + (category != null ? category : NewsCategory.OTHER) +
                ", publishedAt=" + publishedAt +
                '}';
    }
}
