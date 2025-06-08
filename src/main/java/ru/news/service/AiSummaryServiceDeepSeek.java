package ru.news.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.news.config.AppConfig;
import ru.news.model.News;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

/**
 * Сервис для создания AI-сумморизации новостей
 * Использует DeepSeek-R1 через OpenRouter API для генерации кратких изложений
 */
public class AiSummaryServiceDeepSeek implements AiSummaryService {
    private static final Logger logger = LoggerFactory.getLogger(AiSummaryServiceDeepSeek.class);
    private static final String API_URL = "https://openrouter.ai/api/v1/chat/completions";
    private static final String MODEL_NAME = "deepseek/deepseek-r1:free";
    
    private final AppConfig config;
    private final NewsService newsService;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public AiSummaryServiceDeepSeek(AppConfig config, NewsService newsService) {
        this.config = config;
        this.newsService = newsService;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.objectMapper = new ObjectMapper();
        this.apiKey = config.getOpenRouterApiKey();
        
        if (apiKey == null || apiKey.trim().isEmpty()) {
            logger.warn("OpenRouter API key не настроен");
        }
    }

    /**
     * Создает краткое изложение последних новостей
     */
    @Override
    public String generateTodayNewsSummary() {
        try {
            if (apiKey == null || apiKey.trim().isEmpty()) {
                return "❌ OpenRouter API не настроен. Проверьте конфигурацию.";
            }

            // Получаем последние 10 новостей из базы данных
            logger.info("Запрашиваем последние 10 новостей из базы данных");
            List<News> latestNews = newsService.getLatestNews(10);
            logger.info("Получено {} новостей из базы данных", latestNews.size());

            if (latestNews.isEmpty()) {
                logger.warn("Новостей в базе данных не найдено");
                return "📋 *Новостей в базе данных пока нет.*\n\nПопробуйте обновить новости командой /update";
            }

            // Формируем текст для AI
            String newsText = formatNewsForAI(latestNews);

            // Получаем сумморизацию от DeepSeek
            String summary = requestDeepSeekSummary(newsText, "latest_summary");

            return "📋 *Краткое изложение последних новостей:*\n\n" + summary;

        } catch (Exception e) {
            logger.error("Ошибка при создании сумморизации новостей", e);
            return "❌ Произошла ошибка при создании краткого изложения новостей. Попробуйте позже.";
        }
    }

    /**
     * Создает анализ трендов новостей
     */
    @Override
    public String generateTrendsAnalysis(List<String> trends) {
        try {
            if (apiKey == null || apiKey.trim().isEmpty()) {
                return "❌ OpenRouter API не настроен. Проверьте конфигурацию.";
            }

            if (trends.isEmpty()) {
                return "📈 *Анализ трендов:*\n\nТрендовые темы пока не найдены.";
            }

            String trendsText = String.join(", ", trends);
            String analysisRequest = String.format(
                    "Проанализируй следующие трендовые темы российских новостей: %s. " +
                            "Объясни их важность и возможные причины популярности. " +
                            "Дай краткий прогноз развития событий по каждой теме.",
                    trendsText);

            String analysis = requestDeepSeekSummary(analysisRequest, "trends_analysis");
            return "📈 *Анализ трендовых тем:*\n\n" + analysis;

        } catch (Exception e) {
            logger.error("Ошибка при создании анализа трендов", e);
            return "❌ Произошла ошибка при создании анализа трендов. Попробуйте позже.";
        }
    }

    /**
     * Создает персонализированную сводку по категории
     */
    @Override
    public String generateCategorySummary(String categoryName) {
        try {
            if (apiKey == null || apiKey.trim().isEmpty()) {
                return "❌ OpenRouter API не настроен. Проверьте конфигурацию.";
            }

            // Получаем новости за последние 3 дня по категории
            List<News> categoryNews = newsService.getRecentNewsByCategory(categoryName, 3);

            if (categoryNews.isEmpty()) {
                return String.format(
                        "📂 *Сводка по категории \"%s\":*\n\nНовостей по данной категории за последние дни не найдено.",
                        categoryName);
            }

            String newsText = formatNewsForAI(categoryNews);
            String summaryRequest = String.format(
                    "Создай тематическую сводку новостей по категории \"%s\". " +
                            "Выдели ключевые события, основные тенденции и важные факты. " +
                            "Структурируй ответ по важности событий:",
                    categoryName);

            String summary = requestDeepSeekSummary(newsText + "\n\n" + summaryRequest, "category_summary");
            return String.format("📂 *Сводка по категории \"%s\":*\n\n%s", categoryName, summary);

        } catch (Exception e) {
            logger.error("Ошибка при создании сводки по категории", e);
            return "❌ Произошла ошибка при создании сводки по категории. Попробуйте позже.";
        }
    }

    /**
     * Форматирует новости для отправки в AI
     */
    private String formatNewsForAI(List<News> newsList) {
        return newsList.stream()
                .limit(10) // Ограничиваем количество новостей для AI
                .map(news -> String.format("Заголовок: %s\nКатегория: %s\nКраткое содержание: %s\n",
                        news.getTitle(),
                        news.getCategory().getDisplayName(),
                        news.getContent().substring(0, Math.min(200, news.getContent().length())) + "..."))
                .collect(Collectors.joining("\n---\n"));
    }

    /**
     * Отправляет запрос к DeepSeek через OpenRouter API для создания сумморизации
     */
    private String requestDeepSeekSummary(String newsText, String requestType) throws Exception {
        String systemPrompt;
        String userPrompt;

        switch (requestType) {
            case "trends_analysis":
                systemPrompt = "Ты - профессиональный аналитик новостей и политический обозреватель. " +
                        "Анализируй тренды и объясняй их значимость на русском языке.";
                userPrompt = newsText; // В этом случае newsText уже содержит инструкции для анализа трендов
                break;

            case "category_summary":
                systemPrompt = "Ты - профессиональный обозреватель, специализирующийся на тематических новостях. " +
                        "Создавай структурированные сводки по категориям на русском языке.";
                userPrompt = newsText; // В этом случае newsText уже содержит инструкции для сводки по категории
                break;

            case "latest_summary":
            default:
                systemPrompt = "Ты - профессиональный журналист. Создавай краткие и информативные сводки новостей на русском языке.";
                userPrompt = "Создай краткое изложение следующих новостей на русском языке. " +
                        "Выдели основные темы и события. Ответ должен быть структурированным и легко читаемым:\n\n" +
                        newsText;
                break;
        }

        return sendDeepSeekRequest(systemPrompt, userPrompt);
    }
    
    /**
     * Отправляет запрос к DeepSeek через OpenRouter API для создания сумморизации
     */
    private String requestDeepSeekSummary(String newsText) throws Exception {
        return requestDeepSeekSummary(newsText, "latest_summary");
    }

    /**
     * Отправляет запрос к DeepSeek через OpenRouter API с указанными системным и пользовательским
     * промптами
     */
    private String sendDeepSeekRequest(String systemPrompt, String userPrompt) throws Exception {
        try {
            // Создаем JSON для запроса
            ObjectNode requestJson = objectMapper.createObjectNode();
            requestJson.put("model", MODEL_NAME);
            
            ArrayNode messagesArray = objectMapper.createArrayNode();
            
            // Добавляем системное сообщение, если оно есть
            if (systemPrompt != null && !systemPrompt.isEmpty()) {
                ObjectNode systemMessage = objectMapper.createObjectNode();
                systemMessage.put("role", "system");
                systemMessage.put("content", systemPrompt);
                messagesArray.add(systemMessage);
            }
            
            // Добавляем пользовательское сообщение
            ObjectNode userMessage = objectMapper.createObjectNode();
            userMessage.put("role", "user");
            userMessage.put("content", userPrompt);
            messagesArray.add(userMessage);
            
            requestJson.set("messages", messagesArray);
            requestJson.put("temperature", 0.3);
            requestJson.put("max_tokens", 2500);
            
            String requestBody = objectMapper.writeValueAsString(requestJson);

            // Создаем HTTP запрос
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(Duration.ofSeconds(60))
                    .build();

            // Отправляем запрос и получаем ответ
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() != 200) {
                logger.error("Ошибка API: {} - {}", response.statusCode(), response.body());
                throw new RuntimeException("Ошибка при обращении к OpenRouter API: " + response.statusCode());
            }

            // Парсим ответ
            ObjectNode jsonResponse = objectMapper.readValue(response.body(), ObjectNode.class);
            String content = jsonResponse.path("choices").path(0).path("message").path("content").asText();
            
            if (content == null || content.isEmpty()) {
                logger.warn("Получен пустой ответ от OpenRouter API");
                return "Не удалось получить ответ от AI сервиса.";
            }
            
            // Проверка на обрыв ответа
            if (content.endsWith("...") || !content.endsWith(".") && !content.endsWith("!") && !content.endsWith("?") && content.length() >= 2450) {
                logger.warn("Получен, возможно, обрезанный ответ от OpenRouter API");
                content += "...";
            }
            
            return content;

        } catch (Exception e) {
            logger.error("Ошибка при обращении к OpenRouter API", e);
            throw new RuntimeException("Ошибка при обращении к OpenRouter API: " + e.getMessage(), e);
        }
    }
} 