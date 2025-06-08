package ru.news.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.news.model.News;

import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Сервис для экспорта новостей в различные форматы
 */
public class ExportService {
    private static final Logger logger = LoggerFactory.getLogger(ExportService.class);
    private final ObjectMapper objectMapper;
    
    // Кэш последних экспортов
    private final ConcurrentMap<String, String> exportCache = new ConcurrentHashMap<>();
    private static final int MAX_CACHE_ENTRIES = 10;
    
    // Форматтеры дат для различных форматов
    private static final DateTimeFormatter CSV_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter HTML_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    public ExportService() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * Экспорт новостей в JSON формат
     */
    public String exportToJson(List<News> newsList) {
        if (newsList == null) {
            logger.warn("Попытка экспорта null списка новостей в JSON");
            return "[]";
        }
        
        if (newsList.isEmpty()) {
            return "[]";
        }
        
        // Проверяем кэш
        String cacheKey = "json_" + computeListHashCode(newsList);
        String cached = exportCache.get(cacheKey);
        if (cached != null) {
            logger.debug("Используем кэшированный JSON экспорт");
            return cached;
        }
        
        try {
            String json = objectMapper.writeValueAsString(newsList);
            cacheExportResult(cacheKey, json);
            return json;
        } catch (Exception e) {
            logger.error("Ошибка при экспорте в JSON: {}", e.getMessage(), e);
            return "{ \"error\": \"Ошибка при экспорте в JSON\" }";
        }
    }

    /**
     * Экспорт новостей в CSV формат
     */
    public String exportToCsv(List<News> newsList) {
        if (newsList == null) {
            logger.warn("Попытка экспорта null списка новостей в CSV");
            return "ID,Заголовок,Описание,URL,Категория,Дата публикации\n";
        }
        
        // Проверяем кэш
        String cacheKey = "csv_" + computeListHashCode(newsList);
        String cached = exportCache.get(cacheKey);
        if (cached != null) {
            logger.debug("Используем кэшированный CSV экспорт");
            return cached;
        }

        StringBuilder csv = new StringBuilder();
        csv.append("ID,Заголовок,Описание,URL,Категория,Дата публикации\n");

        for (News news : newsList) {
            if (news == null) continue;
            
            appendCsvRow(csv, news);
        }
        
        String result = csv.toString();
        cacheExportResult(cacheKey, result);
        return result;
    }
    
    /**
     * Добавление строки CSV для новости
     */
    private void appendCsvRow(StringBuilder csv, News news) {
        csv.append(news.getId()).append(",")
                .append("\"").append(escapeForCsv(news.getTitle())).append("\",")
                .append("\"").append(escapeForCsv(news.getDescription())).append("\",")
                .append("\"").append(escapeForCsv(news.getUrl())).append("\",")
                .append("\"").append(news.getCategory() != null ? news.getCategory().getDisplayName() : "").append("\",")
                .append("\"").append(news.getPublishedAt() != null ? 
                        news.getPublishedAt().format(CSV_DATE_FORMATTER) : "").append("\"")
                .append("\n");
    }

    /**
     * Экспорт новостей в HTML формат
     */
    public String exportToHtml(List<News> newsList) {
        if (newsList == null) {
            logger.warn("Попытка экспорта null списка новостей в HTML");
            return createHtmlWrapper(Collections.emptyList());
        }
        
        // Проверяем кэш
        String cacheKey = "html_" + computeListHashCode(newsList);
        String cached = exportCache.get(cacheKey);
        if (cached != null) {
            logger.debug("Используем кэшированный HTML экспорт");
            return cached;
        }

        String html = createHtmlWrapper(newsList);
        cacheExportResult(cacheKey, html);
        return html;
    }
    
    /**
     * Создание HTML документа с новостями
     */
    private String createHtmlWrapper(List<News> newsList) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n")
                .append("<html lang=\"ru\">\n")
                .append("<head>\n")
                .append("    <meta charset=\"UTF-8\">\n")
                .append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n")
                .append("    <title>Экспорт новостей</title>\n")
                .append("    <style>\n")
                .append("        body { font-family: Arial, sans-serif; margin: 20px; }\n")
                .append("        .news-item { border: 1px solid #ccc; margin: 10px 0; padding: 15px; }\n")
                .append("        .title { font-size: 18px; font-weight: bold; color: #333; }\n")
                .append("        .category { color: #666; font-size: 12px; }\n")
                .append("        .date { color: #999; font-size: 12px; }\n")
                .append("        .description { margin: 10px 0; }\n")
                .append("    </style>\n")
                .append("</head>\n")
                .append("<body>\n")
                .append("    <h1>Экспорт новостей</h1>\n");

        for (News news : newsList) {
            if (news == null) continue;
            appendHtmlNewsItem(html, news);
        }

        html.append("</body>\n").append("</html>");
        return html.toString();
    }
    
    /**
     * Добавление HTML разметки для одной новости
     */
    private void appendHtmlNewsItem(StringBuilder html, News news) {
        html.append("    <div class=\"news-item\">\n")
                .append("        <div class=\"title\">").append(escapeForHtml(news.getTitle())).append("</div>\n")
                .append("        <div class=\"category\">Категория: ").
                    append(news.getCategory() != null ? news.getCategory().getDisplayName() : "Неизвестно")
                .append("</div>\n")
                .append("        <div class=\"date\">Дата: ").
                    append(news.getPublishedAt() != null ? news.getPublishedAt().format(HTML_DATE_FORMATTER) : "Не указана")
                .append("</div>\n")
                .append("        <div class=\"description\">").
                    append(escapeForHtml(news.getDescription()))
                .append("</div>\n");

        if (news.getUrl() != null && !news.getUrl().isEmpty()) {
            html.append("        <div><a href=\"").append(escapeForHtml(news.getUrl()))
                .append("\" target=\"_blank\">Читать полностью</a></div>\n");
        }
        
        html.append("    </div>\n");
    }

    /**
     * Вычисление хэш-кода для списка новостей
     */
    private int computeListHashCode(List<News> newsList) {
        int hash = 0;
        if (newsList.isEmpty()) {
            return 0;
        }
        
        // Используем первые и последние элементы и размер для быстрого вычисления хэш-кода
        hash = 31 * hash + newsList.size();
        hash = 31 * hash + (newsList.get(0) != null ? newsList.get(0).hashCode() : 0);
        
        if (newsList.size() > 1) {
            hash = 31 * hash + (newsList.get(newsList.size() - 1) != null ? 
                newsList.get(newsList.size() - 1).hashCode() : 0);
        }
        
        return hash;
    }
    
    /**
     * Кэширование результата экспорта
     */
    private void cacheExportResult(String key, String result) {
        // Ограничиваем размер кэша
        if (exportCache.size() >= MAX_CACHE_ENTRIES) {
            // Удаляем случайный элемент
            String randomKey = exportCache.keySet().iterator().next();
            exportCache.remove(randomKey);
        }
        exportCache.put(key, result);
    }
    
    /**
     * Очистка кэша экспорта
     */
    public void clearCache() {
        exportCache.clear();
        logger.debug("Кэш экспорта очищен");
    }

    /**
     * Экранирование для CSV
     */
    private String escapeForCsv(String text) {
        if (text == null)
            return "";
        return text.replace("\"", "\"\"");
    }

    /**
     * Экранирование для HTML
     */
    private String escapeForHtml(String text) {
        if (text == null)
            return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}