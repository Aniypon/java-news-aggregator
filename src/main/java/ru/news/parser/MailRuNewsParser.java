package ru.news.parser;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.news.exception.NewsParsingException;
import ru.news.model.News;
import ru.news.model.NewsCategory;
import ru.news.util.HttpUtils;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Парсер новостей с сайта Mail.ru
 */
public class MailRuNewsParser extends AbstractNewsParser {
    private static final Logger logger = LoggerFactory.getLogger(MailRuNewsParser.class);
    private static final String BASE_URL = "https://news.mail.ru";

    public MailRuNewsParser() {
        super(BASE_URL);
    }

    @Override
    protected List<News> parseNewsFromDocument(Document document) {
        List<News> newsList = new ArrayList<>();

        // Используем новый основной селектор на основе анализа HTML
        Elements newsElements = document.select("div[data-qa=\"ArticleTeaser\"]");

        if (newsElements.isEmpty()) {
            logger.warn("Не найдены элементы по основному селектору div[data-qa=\"ArticleTeaser\"], пробуем старые/альтернативные");
            // Старый основной селектор
            newsElements = document.select("[data-qa=\"ArticleTeaser\"] h3[data-qa=\"Title\"] a");
            if (newsElements.isEmpty()) {
                // Альтернативные селекторы на случай изменения структуры
                newsElements = document.select("a[href*=\"news.mail.ru\"]");
            }
            if (newsElements.isEmpty()) {
                logger.warn("Пробуем найти новости по более общему селектору");
                newsElements = document.select("a[href*=\"/news/\"]");
            }
        }

        List<Element> processedElements = new ArrayList<>();
        int parsed = 0;
        for (Element newsItemContainer : newsElements) {
            if (parsed >= maxNews)
                break;

            Element linkElement = newsItemContainer.selectFirst("h3[data-qa=\"Title\"] a");
            if (linkElement == null) {
                // Если это уже ссылка (старый селектор), используем ее напрямую
                if (newsItemContainer.tagName().equals("a")) {
                    linkElement = newsItemContainer;
                } else {
                    logger.warn("Не найден элемент ссылки h3[data-qa=\"Title\"] a в контейнере {}", newsItemContainer.html());
                    continue;
                }
            }

            String url = linkElement.absUrl("href");
            // Проверка на дубликаты ссылок
            boolean isDuplicate = processedElements.stream()
                    .anyMatch(e -> {
                        Element innerLink = e.selectFirst("h3[data-qa=\"Title\"] a");
                        if (innerLink != null) return innerLink.absUrl("href").equals(url);
                        if (e.tagName().equals("a")) return e.absUrl("href").equals(url); // для старых селекторов
                        return false;
                    });

            if (isDuplicate) {
                continue;
            }

            try {
                // Передаем контейнер элемента новости, а не только ссылку
                News news = parseNewsElement(newsItemContainer, linkElement);
                if (news != null) {
                    newsList.add(news);
                    processedElements.add(newsItemContainer); // Добавляем контейнер для проверки дубликатов
                    parsed++;
                    logger.debug("Добавлена новость Mail.ru: {}", news.getTitle());
                }
            } catch (Exception e) {
                logger.warn("Ошибка при парсинге элемента Mail.ru: {} для URL: {}", e.getMessage(), url);
            }
        }

        return newsList;
    }

    private News parseNewsElement(Element newsItemContainer, Element linkElement) {
        try {
            String title = linkElement.text().trim();
            String url = linkElement.absUrl("href");

            if (title.isEmpty() || url.isEmpty() || !url.contains("news.mail.ru")) {
                logger.debug("Пропущен элемент: title='{}', url='{}'", title, url);
                return null;
            }

            // Поиск изображения
            String imageUrl = extractImageUrl(newsItemContainer);

            // Поиск описания/краткого содержания
            String description = extractDescription(newsItemContainer);

            // Получить полный контент, если возможно
            String fullContent = fetchFullContent(url);
            String content = fullContent.isEmpty() ? description : fullContent;

            // Поиск времени публикации
            LocalDateTime publishedAt = extractPublicationDate(newsItemContainer, url);

            News news = createNewsObject(title, description, content, url, imageUrl);
            news.setPublishedAt(publishedAt);

            return news;
        } catch (Exception e) {
            logger.warn("Ошибка при парсинге элемента Mail.ru: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Находит контейнер новости - теперь это сам newsItemContainer
     */
    private Element findNewsContainer(Element element) {
        // Теперь элемент, передаваемый в parseNewsElement - это уже newsItemContainer
        // Этот метод может не понадобиться или его логика должна быть пересмотрена,
        // если структура предполагает более глубокий поиск от linkElement.
        // На данный момент, newsItemContainer это div[data-qa="ArticleTeaser"]
        return element;
    }

    /**
     * Извлекает URL изображения
     */
    private String extractImageUrl(Element container) {
        // Новый селектор для изображения
        Element pictureElement = container.selectFirst("picture[data-qa=\"Picture\"]");
        if (pictureElement != null) {
            Element imgElement = pictureElement.selectFirst("img");
            if (imgElement != null) {
                String imageUrl = imgElement.absUrl("src");
                if (imageUrl.isEmpty()) {
                    imageUrl = imgElement.attr("data-src"); // На случай ленивой загрузки
                }
                // Можно также проверить <source> элементы внутри <picture> для webp или других форматов
                if (imageUrl.isEmpty()) {
                    Elements sourceElements = pictureElement.select("source[srcset]");
                    if (!sourceElements.isEmpty()) {
                        // Берем первый попавшийся srcset, разделяем и берем первую ссылку
                        String srcset = sourceElements.first().attr("srcset");
                        if (srcset != null && !srcset.isEmpty()) {
                            imageUrl = srcset.split(",")[0].split(" ")[0];
                        }
                    }
                }
                return normalizeImageUrl(imageUrl);
            }
        }
        // Старый поиск, если новый не сработал
        Element imgElement = container.selectFirst("img");
        if (imgElement == null) {
            return null;
        }

        String imageUrl = imgElement.absUrl("src");
        if (imageUrl.isEmpty()) {
            imageUrl = imgElement.attr("data-src");
        }

        return normalizeImageUrl(imageUrl);
    }

    /**
     * Извлекает описание новости
     */
    private String extractDescription(Element container) {
        // Новый селектор для описания, ищем div с data-qa="Text" и определенным классом (может быть несколько)
        // Пример: <div data-qa="Text" class="cca994f104 f63c3a51cb c7aed0d7f3">...</div>
        Element descElement = container.selectFirst("div[data-qa=\"Text\"].f63c3a51cb, div[data-qa=\"Text\"].c7aed0d7f3");
        if (descElement == null) {
            // Старый селектор, если новый не сработал
            descElement = container.selectFirst("[data-qa=\"Summary\"], .annotation, .lead");
        }
        return descElement != null ? descElement.text().trim() : "";
    }

    /**
     * Извлекает дату публикации
     */
    private LocalDateTime extractPublicationDate(Element container, String url) {
        try {
            // Новый селектор для времени, ищем time[datetime] внутри элемента с data-logger="Breadcrumbs"
            Element breadcrumbs = container.selectFirst("[data-logger=\"Breadcrumbs\"]");
            if (breadcrumbs != null) {
                Element timeElement = breadcrumbs.selectFirst("time[datetime]");
                if (timeElement != null) {
                    String datetime = timeElement.attr("datetime");
                    if (!datetime.isEmpty()) {
                        // UTC dates end with Z or have offset like +03:00
                        if (datetime.endsWith("Z")) {
                           // datetime = datetime.replace("Z", ""); // LocalDateTime.parse() handles Z correctly
                           return LocalDateTime.parse(datetime, DateTimeFormatter.ISO_DATE_TIME);
                        } else if (datetime.contains("+") || datetime.contains("-")) {
                            // Parse with offset
                            return LocalDateTime.parse(datetime, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                        }
                        return LocalDateTime.parse(datetime); // Если формат без смещения и Z
                    }
                }
            }

            // Старый поиск, если новый не сработал
            Element timeElement = container.selectFirst("time[datetime]");
            if (timeElement != null) {
                String datetime = timeElement.attr("datetime");
                if (!datetime.isEmpty()) {
                    if (datetime.endsWith("Z")) {
                        return LocalDateTime.parse(datetime, DateTimeFormatter.ISO_DATE_TIME);
                    } else if (datetime.contains("+") || datetime.contains("-")) {
                        return LocalDateTime.parse(datetime, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                    }
                    return LocalDateTime.parse(datetime);
                }
            }
            // Попытка получить дату из полной страницы новости, если не найдена в контейнере
            return fetchPublicationDateFromArticle(url);
        } catch (DateTimeParseException e) {
            logger.debug("Не удалось распарсить время: {} для даты '{}'", e.getMessage(), container.selectFirst("time[datetime]") != null ? container.selectFirst("time[datetime]").attr("datetime") : "null");
        } catch (Exception e) {
            logger.debug("Ошибка при получении времени публикации: {}", e.getMessage());
        }
        return LocalDateTime.now(); // Fallback
    }

    /**
     * Получает дату публикации из страницы статьи
     */
    private LocalDateTime fetchPublicationDateFromArticle(String url) {
        try {
            // Используем HttpUtils для получения документа с поддержкой повторных попыток
            Document articleDoc = HttpUtils.fetchDocumentWithRetry(url);
            Element metaDate = articleDoc.selectFirst("meta[property='article:published_time']");
            if (metaDate != null) {
                String dateStr = metaDate.attr("content");
                if (!dateStr.isEmpty()) {
                    // Стандартный формат в meta тегах
                    DateTimeFormatter formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
                    return LocalDateTime.parse(dateStr, formatter);
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
    private String fetchFullContent(String url) throws NewsParsingException {
        if (url == null || url.isEmpty()) {
            logger.warn("URL для получения полного контента пуст.");
            return "";
        }
        
        try {
            logger.debug("Загрузка полного контента для URL: {}", url);
            // Используем HttpUtils для загрузки документа с поддержкой повторных попыток
            Document articleDoc = HttpUtils.fetchDocumentWithRetry(url);

            // Обновленные селекторы для Mail.ru
            Element articleBody = articleDoc.selectFirst("div[data-qa=\"Text\"], div.article__text, div.article__item_html");
            if (articleBody == null) {
                 articleBody = articleDoc.selectFirst("div[itemprop=\"articleBody\"]"); // Более общий селектор
            }
             if (articleBody == null) {
                articleBody = articleDoc.selectFirst(".article-text-body");
            }
            if (articleBody == null) {
                logger.warn("Не удалось найти основной контент статьи для URL: {}", url);
                return "";
            }

            // Удаляем ненужные элементы (скрипты, рекламу, кнопки "Поделиться" и т.д.)
            articleBody.select("script, style, .social, .subscribe-form, .article-incut, .news-item_ad").remove();

            // Обработка абзацев
            StringBuilder contentBuilder = new StringBuilder();
            Elements paragraphs = articleBody.select("p, div.article__item_html > div"); // Mail.ru иногда использует div вместо p

            if (paragraphs.isEmpty()) { // Если параграфы не найдены, берем весь текст из articleBody
                contentBuilder.append(articleBody.text().trim());
            } else {
                for (Element p : paragraphs) {
                    String paragraphText = p.text().trim();
                    if (!paragraphText.isEmpty()) {
                        contentBuilder.append(paragraphText).append("\n\n");
                    }
                }
            }

            String fullText = contentBuilder.toString().trim();
            logger.trace("Полный текст для {}: {}", url, fullText.substring(0, Math.min(fullText.length(), 200)));
            return fullText;

        } catch (Exception e) {
            logger.error("Неожиданная ошибка при извлечении полного контента для URL {}: {}", url, e.getMessage());
            return ""; // Возвращаем пустую строку в случае ошибок
        }
    }

    @Override
    public String getSourceName() {
        return "Mail.ru";
    }

    /**
     * Создает объект News.
     */
    @Override
    protected News createNewsObject(String title, String description, String content, String url, String imageUrl) {
        News news = new News();
        news.setTitle(title);
        news.setDescription(description);
        news.setContent(content);
        news.setUrl(url);
        news.setImageUrl(imageUrl);
        news.setSource(getSourceName());
        
        // Добавляем вызов recategorize для установки категории
        news.recategorize();
        
        // Проверка на null и установка значения по умолчанию
        if (news.getCategory() == null) {
            news.setCategory(NewsCategory.OTHER);
        }
        
        return news;
    }

    /**
     * Нормализует URL изображения, если он относительный или имеет префикс //
     */
    @Override
    protected String normalizeImageUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            return null;
        }
        if (imageUrl.startsWith("//")) {
            return "https:" + imageUrl;
        }
        if (!imageUrl.startsWith("http://") && !imageUrl.startsWith("https://")) {
            // Попытка сделать его абсолютным, если он относительный.
            // Это может потребовать базового URL, если он действительно относительный.
            // Однако, Jsoup.absUrl(\"src\") должен был уже это сделать.
            // Оставляем как есть, если не начинается с http, предполагая, что это может быть data URI или другая схема.
        }
        return imageUrl;
    }
}
