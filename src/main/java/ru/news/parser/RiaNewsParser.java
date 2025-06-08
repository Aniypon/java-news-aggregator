package ru.news.parser;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.news.exception.NewsParsingException;
import ru.news.model.News;
import ru.news.util.HttpUtils;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Парсер новостей с сайта РИА Новости
 */
public class RiaNewsParser extends AbstractNewsParser {
    private static final Logger logger = LoggerFactory.getLogger(RiaNewsParser.class);
    private static final String NEWS_URL = "https://ria.ru/lenta/";

    // Паттерн для извлечения даты из метаданных
    private static final Pattern DATE_PATTERN = Pattern.compile("\\d{2}[.]\\d{2}[.]\\d{4}");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy", new Locale("ru"));
    private static final Pattern URL_DATE_PATTERN = Pattern.compile("/(\\d{4})(\\d{2})(\\d{2})/");
    private static final DateTimeFormatter URL_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    public RiaNewsParser() {
        super("https://ria.ru");
    }

    @Override
    protected String getNewsUrl() {
        return NEWS_URL;
    }

    @Override
    protected List<News> parseNewsFromDocument(Document document) {
        List<News> newsList = new ArrayList<>();
        
        // Основной селектор для списка новостей
        Elements newsElements = document.select(".list-item");
        
        int parsed = 0;
        for (Element element : newsElements) {
            if (parsed >= maxNews)
                break;
                
            try {
                News news = parseNewsElement(element);
                if (news != null) {
                    newsList.add(news);
                    parsed++;
                    logger.debug("Добавлена новость РИА: {}", news.getTitle());
                }
            } catch (Exception e) {
                logger.warn("Ошибка при парсинге элемента новости РИА: {}", e.getMessage());
            }
        }
        
        return newsList;
    }

    private News parseNewsElement(Element element) {
        try {
            // Если элемент сам является ссылкой - эта логика больше не нужна с новым основным селектором
            Element linkElement = element.selectFirst("a.list-item__title");
            
            if (linkElement == null)
                return null;
                
            String title = linkElement.text().trim();
            String relativeUrl = linkElement.attr("href");
            
            if (title.isEmpty() || relativeUrl.isEmpty())
                return null;
                
            String fullUrl = normalizeUrl(relativeUrl);
            
            // Описание будет из fetchFullContent
            String description = ""; 
            
            // Получаем полное содержание новости, если возможно
            String content = fetchFullContent(fullUrl);
            if (content.isEmpty()) {
                // Если fetchFullContent ничего не вернул, описание остается пустым
            } else {
                // В текущей логике description не заполняется из list-item,
                // поэтому если content есть, он и будет основным текстом.
                // Если нужно краткое описание отдельно, это потребует другой логики.
            }
            
            // Получаем изображение
            Element imgElement = element.selectFirst(".list-item__image img");
            String imageUrl = null;
            if (imgElement != null) {
                imageUrl = imgElement.attr("src");
                if (imageUrl.isEmpty()) {
                    imageUrl = imgElement.attr("data-src");
                }
                imageUrl = normalizeImageUrl(imageUrl);
            }
            
            // Получаем дату публикации
            LocalDateTime publishedAt = extractPublicationDate(element);
            
            News news = createNewsObject(title, description, content, fullUrl, imageUrl);
            if (publishedAt != null) {
                news.setPublishedAt(publishedAt);
            }
            
            return news;
            
        } catch (Exception e) {
            logger.warn("Ошибка при парсинге элемента РИА: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Извлекает дату публикации из элемента новости
     */
    private LocalDateTime extractPublicationDate(Element element) {
        LocalDateTime dateTime = null;
        String newsUrl = "";
        try {
            Element linkElement = element.selectFirst("a.list-item__title");
            if (linkElement != null) {
                newsUrl = linkElement.attr("href");
            }

            // 1. Попытка извлечь дату из URL
            Matcher urlMatcher = URL_DATE_PATTERN.matcher(newsUrl);
            LocalDate dateFromUrl = null;
            if (urlMatcher.find()) {
                String year = urlMatcher.group(1);
                String month = urlMatcher.group(2);
                String day = urlMatcher.group(3);
                dateFromUrl = LocalDate.parse(year + month + day, URL_DATE_FORMATTER);
            }

            // 2. Попытка извлечь время из div.list-item__info-item[data-type=date]
            LocalTime timeFromDiv = null;
            Element timeElement = element.selectFirst("div.list-item__info-item[data-type=date]");
            if (timeElement != null) {
                String timeText = timeElement.text().trim(); // e.g., "20:06"
                try {
                    timeFromDiv = LocalTime.parse(timeText, TIME_FORMATTER);
                } catch (Exception e) {
                    logger.debug("Не удалось извлечь время из '{}': {}", timeText, e.getMessage());
                }
            }

            // Комбинирование даты и времени
            if (dateFromUrl != null && timeFromDiv != null) {
                dateTime = LocalDateTime.of(dateFromUrl, timeFromDiv);
            } else if (dateFromUrl != null) {
                dateTime = LocalDateTime.of(dateFromUrl, LocalTime.of(12, 0)); // Время по умолчанию 12:00
            } else if (timeFromDiv != null) {
                dateTime = LocalDateTime.of(LocalDate.now(), timeFromDiv); // Дата по умолчанию - сегодня
            }

            // 3. Старая логика как fallback (из .list-item__date)
            if (dateTime == null) {
                Element dateElementOld = element.select(".list-item__date").first();
                if (dateElementOld != null) {
                    String dateText = dateElementOld.text().trim();
                    Matcher matcherOld = DATE_PATTERN.matcher(dateText);
                    if (matcherOld.find()) {
                        String dateStr = matcherOld.group();
                        dateTime = LocalDate.parse(dateStr, DATE_FORMATTER).atTime(12,0); // Примерное время
                    }
                }
            }

        } catch (Exception e) {
            logger.debug("Не удалось извлечь дату и время: {}", e.getMessage());
        }
        
        // 4. Финальный fallback
        return dateTime != null ? dateTime : LocalDateTime.now();
    }
    
    /**
     * Получение полного содержимого новости по URL
     */
    private String fetchFullContent(String url) {
        try {
            // Используем HttpUtils для получения документа с поддержкой повторных попыток
            Document articleDoc = HttpUtils.fetchDocumentWithRetry(url);
            
            // Извлекаем основной контент
            Elements contentElements = articleDoc.select(".article__body .article__text");
            
            if (contentElements.isEmpty()) {
                // Пробуем альтернативные селекторы
                contentElements = articleDoc.select(".article__block .article__text, .article__block p");
            }
            
            if (contentElements.isEmpty()) {
                // Еще альтернативные селекторы
                contentElements = articleDoc.select(".article__body p, article p, .article p");
            }
            
            if (!contentElements.isEmpty()) {
                StringBuilder contentBuilder = new StringBuilder();
                for (Element contentEl : contentElements) {
                    contentBuilder.append(contentEl.text()).append("\n");
                }
                return contentBuilder.toString().trim();
            }
        } catch (Exception e) {
            logger.debug("Не удалось получить полный контент новости: {}", e.getMessage());
        }
        
        return "";
    }

    @Override
    public String getSourceName() {
        return "РИА Новости";
    }
}