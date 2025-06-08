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

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Парсер новостей с сайта Rambler News
 */
public class RamblerNewsParser extends AbstractNewsParser {
    private static final Logger logger = LoggerFactory.getLogger(RamblerNewsParser.class);
    private static final String BASE_URL = "https://news.rambler.ru";
    private static final int MAX_NEWS = 10;
    
    // Паттерн для извлечения даты из метаданных
    private static final Pattern DATE_PATTERN = Pattern.compile("\\d{2}[.]\\d{2}[.]\\d{4}");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
        DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"),
        DateTimeFormatter.ofPattern("d MMMM yyyy HH:mm", new java.util.Locale("ru")),
        DateTimeFormatter.ofPattern("d MMMM HH:mm", new java.util.Locale("ru")),
        DateTimeFormatter.ISO_LOCAL_DATE_TIME,
        DateTimeFormatter.ISO_OFFSET_DATE_TIME
    );
    // Паттерн для извлечения относительного времени (e.g., "4 мин", "1 час")
    private static final Pattern RELATIVE_TIME_PATTERN = Pattern.compile("(\\d+)\\s+(мин|час|дн)");

    public RamblerNewsParser() {
        super(BASE_URL);
    }

    @Override
    public List<News> parseNews() throws NewsParsingException {
        List<News> newsList = new ArrayList<>();
        try {
            logger.info("Начинаем парсинг новостей с Rambler News");
            Document document = Jsoup.connect(BASE_URL)
                    .userAgent(
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/95.0.4638.69 Safari/537.36")
                    .timeout(10000)
                    .get();

            newsList = parseNewsFromDocument(document);
            logger.info("Спарсено {} новостей с Rambler News", newsList.size());
        } catch (IOException e) {
            throw new NewsParsingException("Ошибка при подключении к Rambler News", e);
        }
        return newsList;
    }

    @Override
    protected List<News> parseNewsFromDocument(Document document) {
        List<News> newsList = new ArrayList<>();

        // Обновленный селектор на основе предоставленного HTML
        Elements newsElements = document.select("div.rc__Ifn4C");

        if (newsElements.isEmpty()) {
            logger.warn("Не найдены элементы новостей по основному селектору 'div.rc__Ifn4C', пробуем старые селекторы");
            // Старые селекторы как fallback
            newsElements = document.select("div.rc__Ifn4C"); // Повтор для ясности, можно удалить если выше остался тот же

            if (newsElements.isEmpty()) {
                logger.warn("Не найдены элементы новостей по селектору 'div.rc__Ifn4C', пробуем альтернативные старые");
                newsElements = document.select("div[class*='rc__'] a[class*='rc__ACOVq']").parents()
                        .select("div[class*='rc__Ifn4C']");
            }

            if (newsElements.isEmpty()) {
                logger.warn("Пробуем найти новости с помощью селектора ссылок 'div.cell'");
                newsElements = document.select("div.cell");
            }

            if (newsElements.isEmpty()) {
                logger.warn("Не удалось найти новости по специфическим селекторам, пробуем общие 'a[href*='/news.rambler.ru/']'");
                newsElements = document.select("a[href*='/news.rambler.ru/'], a[href*='https://news.rambler.ru/']")
                        .parents();
            }
        }

        int parsed = 0;
        for (Element element : newsElements) {
            if (parsed >= MAX_NEWS)
                break;

            try {
                News news = parseNewsElement(element);
                if (news != null) {
                    newsList.add(news);
                    parsed++;
                    logger.debug("Добавлена новость Rambler: {}", news.getTitle());
                }
            } catch (Exception e) {
                logger.warn("Ошибка при парсинге элемента Rambler: {}", e.getMessage(), e);
            }
        }

        return newsList;
    }

    private News parseNewsElement(Element element) {
        try {
            // Поиск ссылки на новость по обновленному селектору
            Element linkElement = element.selectFirst("a.rc__ACOVq");

            if (linkElement == null) {
                // Fallback на старые селекторы, если новый не сработал
                linkElement = element.selectFirst("a[class*='rc__ACOVq']");
                if (linkElement == null) {
                    linkElement = element.selectFirst("a[href*='/news.rambler.ru/'], a[href*='https://news.rambler.ru/'], a.cell-title");
                }
            }

            if (linkElement == null) {
                logger.debug("Link element не найден в {}", element.html());
                return null;
            }

            String title = linkElement.attr("title");
            if (title.isEmpty()) {
                title = linkElement.text().trim();
            }
            String url = linkElement.absUrl("href");

            if (title.isEmpty() || url.isEmpty() || !url.startsWith("http")) {
                logger.debug("Title или URL пустые или некорректные: title='{}', url='{}'", title, url);
                return null;
            }

            // Ищем описание по обновленному селектору
            String description = "";
            Element descElement = element.selectFirst("div.rc__DHMU-");
            if (descElement == null) {
                // Fallback на старые селекторы
                descElement = element.selectFirst("div[class*='rc__DHMU-'], div[class*='description'], div[class*='rc__I0pXf'], div.cell-text");
            }
            if (descElement != null) {
                description = descElement.text().trim();
            }

            // Поиск изображения по обновленному селектору
            Element imgElement = element.selectFirst("img.rc__3-qh-");
            if (imgElement == null) {
                // Fallback на старые селекторы
                imgElement = element.selectFirst("img[class*='rc__3-qh-'], img.cell-image");
            }

            String imageUrl = null;
            if (imgElement != null) {
                imageUrl = imgElement.attr("src"); // Основной источник
                if (imageUrl.isEmpty() || !imageUrl.startsWith("http")) {
                    // Пытаемся извлечь из srcset первый URL
                    String srcset = imgElement.attr("srcset");
                    if (!srcset.isEmpty()) {
                        String[] sources = srcset.split(",");
                        if (sources.length > 0) {
                            String firstSource = sources[0].trim().split("\s+")[0];
                            if (firstSource.startsWith("//")) {
                                imageUrl = "https:" + firstSource;
                            } else if (firstSource.startsWith("http")) {
                                imageUrl = firstSource;
                            }
                        }
                    }
                }
                if (imageUrl == null || imageUrl.isEmpty()) { // Дополнительный fallback на data-src
                    imageUrl = imgElement.attr("data-src");
                }
                imageUrl = normalizeImageUrl(imageUrl);
            }
            
            // Получение полного содержания
            String content = fetchFullContent(url);
            if (content.isEmpty()) {
                content = description;
            }
            
            // Попытка получить дату публикации
            LocalDateTime publishedAt = extractPublicationDate(element, url);

            News news = createNewsObject(title, description, content, url, imageUrl);
            news.setPublishedAt(publishedAt);

            return news;
        } catch (Exception e) {
            logger.warn("Ошибка при парсинге Rambler элемента: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Извлекает дату публикации из элемента или страницы новости
     */
    private LocalDateTime extractPublicationDate(Element element, String url) {
        try {
            // Попытка извлечь дату из блока <div class="rc__MHPak">
            Element metaInfoElement = element.selectFirst("div.rc__MHPak");
            if (metaInfoElement != null) {
                String timeAgoText = "";
                Element timeAgoElement = metaInfoElement.selectFirst("span.rc__zoBko");
                Element timeUnitElement = metaInfoElement.selectFirst("span.rc__FaTMf"); // "назад"

                if (timeAgoElement != null) {
                    timeAgoText = timeAgoElement.text().trim(); // e.g., "4 мин", "1 час"
                     if (timeUnitElement != null && "назад".equals(timeUnitElement.text().trim())) {
                        Matcher matcher = RELATIVE_TIME_PATTERN.matcher(timeAgoText);
                        if (matcher.find()) {
                            int amount = Integer.parseInt(matcher.group(1));
                            String unit = matcher.group(2);
                            LocalDateTime now = LocalDateTime.now();
                            switch (unit) {
                                case "мин":
                                    return now.minusMinutes(amount);
                                case "час":
                                    return now.minusHours(amount);
                                case "дн": // Для "дней", если такой формат появится
                                    return now.minusDays(amount);
                                default:
                                    logger.debug("Неизвестная единица времени для относительной даты: {}", unit);
                            }
                        }
                     } else {
                        // Если нет "назад", возможно это просто категория или что-то еще.
                        logger.debug("Элемент времени не содержит 'назад': {}", metaInfoElement.html());
                     }
                } else {
                    logger.debug("Элемент rc__zoBko не найден в {}", metaInfoElement.html());
                }
            }

            // Ищем элемент даты (старая логика как fallback)
            Element dateElement = element.selectFirst("time, span.cell-date, div.cell-date");
            if (dateElement != null) {
                String dateText = dateElement.text().trim();

                Matcher matcher = DATE_PATTERN.matcher(dateText);
                if (matcher.find()) {
                    String dateStr = matcher.group();
                    return LocalDateTime.parse(dateStr, DATE_FORMATTER).withHour(12).withMinute(0); // Примерное время
                }
            }

            // Если не нашли дату в элементе новости, пробуем получить из полной страницы
            return fetchPublicationDateFromArticle(url);
        } catch (Exception e) {
            logger.warn("Не удалось извлечь дату публикации: {}", e.getMessage(), e);
            return LocalDateTime.now(); // Возвращаем текущее время как крайний fallback
        }
    }
    
    /**
     * Получает дату публикации из страницы статьи
     */
    private LocalDateTime fetchPublicationDateFromArticle(String url) {
        try {
            // Используем HttpUtils для получения документа с поддержкой повторных попыток
            Document articleDoc = HttpUtils.fetchDocumentWithRetry(url);
            
            // Ищем мета-тег с датой
            Element metaDate = articleDoc.selectFirst("meta[property='article:published_time']");
            if (metaDate != null) {
                String dateStr = metaDate.attr("content");
                if (!dateStr.isEmpty()) {
                    return LocalDateTime.parse(dateStr.replace("Z", ""));
                }
            }
            
            // Ищем элемент с датой на странице
            Element dateElement = articleDoc.selectFirst("time.article__time");
            if (dateElement != null) {
                String dateText = dateElement.text().trim();
                for (DateTimeFormatter formatter : DATE_FORMATTERS) {
                    try {
                        return LocalDateTime.parse(dateText, formatter);
                    } catch (DateTimeParseException e) {
                        // Продолжаем попытки с другими форматами
                    }
                }
                
                // Еще одна попытка для специальных форматов
                if (dateText.contains(" в ")) {
                    try {
                        // Типичный формат с русскими названиями месяцев: "25 февраля в 12:42"
                        String[] parts = dateText.split(" в ");
                        if (parts.length == 2) {
                            String datePart = parts[0].trim();
                            String timePart = parts[1].trim();
                            
                            // Обрабатываем еще одно возможное разделение
                            // TODO: требуется улучшение этой логики для разных форматов
                            // Все это может быть реализовано лучше с использованием регулярных выражений
                        }
                    } catch (Exception parsingEx) {
                        logger.debug("Ошибка при попытке дополнительного парсинга даты со страницы статьи: {}", parsingEx.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("Ошибка при получении даты из страницы статьи: {}", e.getMessage());
        }
        
        return LocalDateTime.now();
    }
    
    /**
     * Получает полный контент новости
     */
    private String fetchFullContent(String url) {
        try {
            // Используем HttpUtils для получения документа с поддержкой повторных попыток
            Document articleDoc = HttpUtils.fetchDocumentWithRetry(url);
            
            // Ищем основной текст статьи
            Elements contentElements = articleDoc.select("div.article__text p, div.article__paragraph");
            
            if (contentElements.isEmpty()) {
                // Пробуем альтернативные селекторы
                contentElements = articleDoc.select("div.article__body p, div.article__content p, div.article-text p");
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

    private LocalDateTime parseTimeWithKeyword(String text, String keyword, LocalDateTime baseDateTime) {
        Pattern timePattern = Pattern.compile("(\\d{1,2}):(\\d{2})");
        Matcher timeMatcher = timePattern.matcher(text);
        if (timeMatcher.find()) {
            int hours = Integer.parseInt(timeMatcher.group(1));
            int minutes = Integer.parseInt(timeMatcher.group(2));
            return baseDateTime.withHour(hours).withMinute(minutes).withSecond(0).withNano(0);
        }
        return baseDateTime.withHour(12).withMinute(0); // Default time if only keyword found
    }

    @Override
    public String getSourceName() {
        return "Rambler News";
    }
}