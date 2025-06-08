package ru.news.model;

/**
 * Модель для хранения данных о подписке пользователя на категорию новостей
 */
public class UserSubscription {
    private long userId;
    private NewsCategory category;

    public UserSubscription() {
    }

    public UserSubscription(long userId, NewsCategory category) {
        this.userId = userId;
        this.category = category;
    }

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public NewsCategory getCategory() {
        return category;
    }

    public void setCategory(NewsCategory category) {
        this.category = category;
    }

    @Override
    public String toString() {
        return "UserSubscription{" +
                "userId=" + userId +
                ", category=" + category +
                '}';
    }
} 