package ru.news.parser;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.news.exception.NewsParsingException;
import ru.news.model.News;
import ru.news.util.HttpUtils;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Абстрактный класс парсера новостей, содержащий общую логику
 */
public abstract class AbstractNewsParser implements NewsParser {
    private static final Logger logger = LoggerFactory.getLogger(AbstractNewsParser.class);
    private static final int DEFAULT_TIMEOUT_MS = 15000;
    private static final int DEFAULT_MAX_NEWS = 10;
    
    protected final String baseUrl;
    protected final int maxNews;
    
    /**
     * Конструктор с возможностью настройки источника и максимального количества новостей
     * 
     * @param baseUrl базовый URL источника новостей
     */
    protected AbstractNewsParser(String baseUrl) {
        this(baseUrl, DEFAULT_MAX_NEWS);
    }
    
    /**
     * Конструктор с возможностью настройки источника и максимального количества новостей
     * 
     * @param baseUrl базовый URL источника новостей
     * @param maxNews максимальное количество новостей для парсинга
     */
    protected AbstractNewsParser(String baseUrl, int maxNews) {
        this.baseUrl = baseUrl;
        this.maxNews = maxNews;
    }
    
    /**
     * Общая реализация метода парсинга новостей
     */
    @Override
    public List<News> parseNews() throws NewsParsingException {
        List<News> newsList = new ArrayList<>();
        
        try {
            logger.info("Начинаем парсинг новостей с {}", getSourceName());
            
            Document document = getDocument(getNewsUrl());
            
            if (document == null) {
                return newsList;
            }
            
            newsList = parseNewsFromDocument(document);
            
            logger.info("Спарсено {} новостей с {}", newsList.size(), getSourceName());
            
        } catch (IOException e) {
            throw new NewsParsingException("Ошибка при подключении к " + getSourceName(), e);
        }
        
        return newsList;
    }
    
    /**
     * Получение HTML документа
     * 
     * @param url URL для загрузки
     * @return объект Document
     * @throws IOException при ошибке подключения
     */
    protected Document getDocument(String url) throws IOException {
        try {
            // Используем HttpUtils для получения документа с поддержкой повторных попыток
            return HttpUtils.fetchDocumentWithRetry(url);
        } catch (NewsParsingException e) {
            // Преобразуем NewsParsingException в IOException для совместимости
            throw new IOException("Ошибка при загрузке документа: " + url, e);
        }
    }
    
    /**
     * URL для получения новостей
     * По умолчанию совпадает с baseUrl, но может быть переопределен
     */
    protected String getNewsUrl() {
        return baseUrl;
    }
    
    /**
     * Парсинг новостей из HTML документа
     * 
     * @param document HTML документ
     * @return список новостей
     */
    protected abstract List<News> parseNewsFromDocument(Document document);
    
    /**
     * Создание объекта новости с базовыми параметрами
     * 
     * @param title заголовок
     * @param description описание
     * @param content содержимое
     * @param url URL новости
     * @param imageUrl URL изображения
     * @return объект новости
     */
    protected News createNewsObject(String title, String description, String content, String url, String imageUrl) {
        News news = new News(title, description, content, url, getSourceName());
        news.setImageUrl(imageUrl);
        news.setPublishedAt(LocalDateTime.now());
        return news;
    }
    
    /**
     * Нормализует URL, добавляя базовый URL, если необходимо
     * 
     * @param url исходный URL
     * @return нормализованный URL
     */
    protected String normalizeUrl(String url) {
        if (url == null || url.isEmpty()) {
            return "";
        }
        
        if (url.startsWith("http")) {
            return url;
        }
        
        return url.startsWith("/") ? baseUrl + url : baseUrl + "/" + url;
    }
    
    /**
     * Нормализует URL изображения
     * 
     * @param imageUrl исходный URL изображения
     * @return нормализованный URL изображения
     */
    protected String normalizeImageUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            return null;
        }
        
        if (imageUrl.startsWith("http")) {
            return imageUrl;
        }
        
        // Обрабатываем URL-ы с // в начале (протокол-независимые)
        if (imageUrl.startsWith("//")) {
            return "https:" + imageUrl;
        }
        
        return imageUrl.startsWith("/") ? baseUrl + imageUrl : baseUrl + "/" + imageUrl;
    }
} 