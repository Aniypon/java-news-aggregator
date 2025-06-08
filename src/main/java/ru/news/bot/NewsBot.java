package ru.news.bot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import ru.news.config.AppConfig;
import ru.news.model.News;
import ru.news.model.NewsCategory;
import ru.news.service.AnalyticsService;
import ru.news.service.ExportService;
import ru.news.service.NewsParsingService;
import ru.news.service.NewsSchedulerService;
import ru.news.service.NewsService;
import ru.news.service.AiSummaryService;
import ru.news.service.AiSummaryServiceFactory;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Telegram бот для агрегатора новостей
 */
public class NewsBot extends TelegramLongPollingBot {
    private static final Logger logger = LoggerFactory.getLogger(NewsBot.class);

    private final NewsService newsService;
    private final NewsParsingService parsingService;
    private final ExportService exportService;
    private final AnalyticsService analyticsService;
    private final NewsSchedulerService schedulerService;
    private final AiSummaryService aiSummaryService;
    private final AppConfig config;

    public NewsBot(NewsService newsService, NewsParsingService parsingService,
            AnalyticsService analyticsService,
            NewsSchedulerService schedulerService, AppConfig config) {
        super(config.getBotToken());
        this.newsService = newsService;
        this.parsingService = parsingService;
        this.exportService = new ExportService();
        this.analyticsService = analyticsService;
        this.schedulerService = schedulerService;
        
        // Используем фабрику для создания сервиса AI-сумморизации
        AiSummaryServiceFactory aiSummaryServiceFactory = new AiSummaryServiceFactory(config, newsService);
        this.aiSummaryService = aiSummaryServiceFactory.createAiSummaryService();
        
        this.config = config;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            Long chatId = update.getMessage().getChatId();

            logger.info("Получено сообщение от {}: {}", chatId, messageText);

            try {
                handleMessage(chatId, messageText);
            } catch (Exception e) {
                logger.error("Ошибка при обработке сообщения: {}", e.getMessage(), e);
                sendMessage(chatId, "Произошла ошибка при обработке вашего запроса. Попробуйте позже.");
            }
        } else if (update.hasCallbackQuery()) {
            String callbackData = update.getCallbackQuery().getData();
            Long chatId = update.getCallbackQuery().getMessage().getChatId();

            try {
                handleCallbackQuery(chatId, callbackData);
            } catch (Exception e) {
                logger.error("Ошибка при обработке callback: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * Обработка текстовых сообщений
     */
    private void handleMessage(Long chatId, String messageText) {
        switch (messageText.toLowerCase()) {
            case "/start":
                sendWelcomeMessage(chatId);
                break;
            case "/help":
                sendHelpMessage(chatId);
                break;
            case "/latest":
            case "📰 последние новости":
                sendLatestNews(chatId);
                break;
            case "/categories":
            case "📂 категории":
                sendCategoriesMenu(chatId);
                break;
            case "/stats":
            case "📊 статистика":
                sendStats(chatId);
                break;
            case "/update":
            case "🔄 обновить новости":
                updateNews(chatId);
                break;
            case "/export":
            case "📥 экспорт":
                sendExportMenu(chatId);
                break;
            case "/trends":
            case "🔥 тренды":
                sendTrends(chatId);
                break;
            case "/persons":
            case "👤 персоны":
                sendPersons(chatId);
                break;
            case "/activity":
            case "⚡ активность":
                sendActivity(chatId);
                break;
            case "/summary":
            case "📋 краткое изложение":
                sendNewsSummary(chatId);
                break;
            default:
                if (messageText.startsWith("/search ") || messageText.startsWith("🔍")) {
                    String query = messageText.replace("/search ", "").replace("🔍 ", "");
                    searchNews(chatId, query);
                } else if (messageText.startsWith("/export_csv")) {
                    exportNews(chatId, "csv");
                } else if (messageText.startsWith("/export_json")) {
                    exportNews(chatId, "json");
                } else if (messageText.startsWith("/export_html")) {
                    exportNews(chatId, "html");
                } else {
                    sendUnknownCommandMessage(chatId);
                }
                break;
        }
    }

    /**
     * Обработка callback запросов
     */
    private void handleCallbackQuery(Long chatId, String callbackData) {
        if (callbackData.startsWith("category_")) {
            if (callbackData.startsWith("category_summary_")) {
                // Обработка запроса на AI-сводку по категории
                String categoryName = callbackData.replace("category_summary_", "");
                try {
                    NewsCategory category = NewsCategory.valueOf(categoryName);
                    sendCategorySummary(chatId, category.getDisplayName());
                } catch (IllegalArgumentException e) {
                    sendMessage(chatId, "Неизвестная категория: " + categoryName);
                }
            } else {
                // Обычная обработка выбора категории
                String categoryName = callbackData.replace("category_", "");
                try {
                    NewsCategory category = NewsCategory.valueOf(categoryName);
                    sendNewsByCategory(chatId, category);
                } catch (IllegalArgumentException e) {
                    sendMessage(chatId, "Неизвестная категория: " + categoryName);
                }
            }
        } else if (callbackData.startsWith("export_")) {
            String format = callbackData.replace("export_", "");
            exportNews(chatId, format);
        } else if (callbackData.equals("trends_analysis")) {
            // Обработка запроса на AI-анализ трендов
            sendTrendsAnalysis(chatId);
        } else if (callbackData.startsWith("category_summary_")) {
            // Обработка запроса на AI-сводку по категории
            String categoryName = callbackData.replace("category_summary_", "");
            try {
                NewsCategory category = NewsCategory.valueOf(categoryName);
                sendCategorySummary(chatId, category.getDisplayName());
            } catch (IllegalArgumentException e) {
                sendMessage(chatId, "Неизвестная категория: " + categoryName);
            }
        }
    }

    /**
     * Отправка приветственного сообщения
     */
    private void sendWelcomeMessage(Long chatId) {
        String welcomeText = """
                🤖 Добро пожаловать в Агрегатор новостей!

                Я помогу вам быть в курсе последних новостей из российских источников.

                Доступные команды:
                📰 Последние новости
                📂 Категории
                🔍 Поиск новостей
                📊 Статистика
                🔥 Тренды
                👤 Персоны
                🔄 Обновить новости
                📋 Краткое изложение

                Используйте кнопки меню или введите команду вручную.
                """;

        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(welcomeText);
        message.setReplyMarkup(createMainKeyboard());

        try {
            execute(message);
        } catch (TelegramApiException e) {
            logger.error("Ошибка при отправке приветственного сообщения: {}", e.getMessage(), e);
        }
    }

    /**
     * Отправка справочного сообщения
     */
    private void sendHelpMessage(Long chatId) {
        String helpText = """
                📋 Справка по командам:

                📰 /latest - Показать последние новости
                📂 /categories - Показать новости по категориям
                🔍 /search <запрос> - Поиск новостей по ключевым словам
                📊 /stats - Показать статистику по категориям
                🔥 /trends - Показать трендовые темы
                👤 /persons - Показать упоминаемых персон
                ⚡ /activity - Показать активность системы
                🔄 /update - Обновить новости из источников
                📥 /export - Экспорт новостей

                Примеры поиска:
                🔍 /search политика
                🔍 /search экономика
                🔍 /search спорт
                """;

        sendMessage(chatId, helpText);
    }

    /**
     * Отправка последних новостей
     */
    private void sendLatestNews(Long chatId) {
        // Сначала пытаемся получить новости за сегодняшний день
        LocalDate today = LocalDate.now();
        List<News> todayNews = newsService.getNewsByDate(today);

        List<News> newsToShow;
        String headerMessage;

        if (!todayNews.isEmpty()) {
            // Показываем новости за сегодня, ограничивая количество
            newsToShow = todayNews.stream()
                    .limit(config.getMaxNewsPerRequest())
                    .collect(Collectors.toList());
            headerMessage = "📰 Новости за сегодня:";
        } else {
            // Если новостей за сегодня нет, показываем последние новости
            List<News> latestNews = newsService.getLatestNews(config.getMaxNewsPerRequest());
            if (latestNews.isEmpty()) {
                sendMessage(chatId, "📭 На сегодня новостей пока нет. Попробуйте обновить новости командой /update");
                return;
            }
            newsToShow = latestNews;
            headerMessage = "📰 Последние новости:";
        }

        sendMessage(chatId, headerMessage);

        for (News news : newsToShow) {
            String formattedNews = newsService.formatNewsForTelegram(news);
            sendMessage(chatId, formattedNews);
        }
    }

    /**
     * Отправка меню категорий
     */
    private void sendCategoriesMenu(Long chatId) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        // Создаем кнопки для каждой категории
        for (NewsCategory category : NewsCategory.values()) {
            List<InlineKeyboardButton> row = new ArrayList<>();
            InlineKeyboardButton button = new InlineKeyboardButton();
            button.setText(category.getDisplayName());
            button.setCallbackData("category_" + category.name());
            row.add(button);
            keyboard.add(row);
        }

        markup.setKeyboard(keyboard);

        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("📂 Выберите категорию новостей:");
        message.setReplyMarkup(markup);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            logger.error("Ошибка при отправке меню категорий: {}", e.getMessage(), e);
        }
    }

    /**
     * Отправка новостей по категории
     */
    private void sendNewsByCategory(Long chatId, NewsCategory category) {
        List<News> categoryNews = newsService.getNewsByCategory(category, config.getMaxNewsPerRequest());

        if (categoryNews.isEmpty()) {
            sendMessage(chatId, "📭 В категории \"" + category.getDisplayName() + "\" пока нет новостей.");
            return;
        }

        sendMessage(chatId, "📂 Новости в категории \"" + category.getDisplayName() + "\":");

        for (News news : categoryNews) {
            String formattedNews = newsService.formatNewsForTelegram(news);
            sendMessage(chatId, formattedNews);
        }

        // Добавляем кнопку для получения AI-сводки по категории
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        List<InlineKeyboardButton> row = new ArrayList<>();
        InlineKeyboardButton summaryButton = new InlineKeyboardButton();
        summaryButton.setText("📊 Получить AI-сводку по категории");
        summaryButton.setCallbackData("category_summary_" + category.name());
        row.add(summaryButton);
        keyboard.add(row);

        markup.setKeyboard(keyboard);

        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("💡 Вы можете получить AI-сводку по категории \"" + category.getDisplayName() + "\":");
        message.setReplyMarkup(markup);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            logger.error("Ошибка при отправке кнопки AI-сводки: {}", e.getMessage(), e);
        }
    }

    /**
     * Поиск новостей
     */
    private void searchNews(Long chatId, String query) {
        if (query.trim().isEmpty()) {
            sendMessage(chatId,
                    "🔍 Введите поисковый запрос после команды, например: `/search политика` или `/search Путин`");
            return;
        }

        try {
            List<News> searchResults = newsService.searchNews(query, config.getMaxNewsPerRequest());

            if (searchResults.isEmpty()) {
                // Предлагаем альтернативные варианты поиска
                String escapedQuery = escapeMarkdown(query);
                StringBuilder suggestions = new StringBuilder();
                suggestions.append("🔍 По запросу \"").append(escapedQuery).append("\" ничего не найдено\\.\n\n");
                suggestions.append("💡 *Попробуйте:*\n");
                suggestions.append("• Изменить запрос \\(например, только фамилию\\)\n");
                suggestions.append("• Использовать ключевые слова\n");
                suggestions.append("• Проверить правописание\n");
                suggestions.append("• Воспользоваться командой `/persons` для просмотра доступных персон");

                sendMessage(chatId, suggestions.toString());
                return;
            }

            String escapedQuery = escapeMarkdown(query);
            StringBuilder resultsHeader = new StringBuilder();
            resultsHeader.append("🔍 *Результаты поиска по запросу \"").append(escapedQuery).append("\":*\n");
            resultsHeader.append("Найдено: ").append(searchResults.size()).append(" новостей\n\n");

            sendMessage(chatId, resultsHeader.toString());

            for (int i = 0; i < searchResults.size(); i++) {
                News news = searchResults.get(i);
                String formattedNews = newsService.formatNewsForTelegram(news);

                // Добавляем номер новости для удобства
                String numberedNews = String.format("📰 *Новость %d из %d*\n%s",
                        i + 1, searchResults.size(), formattedNews);

                sendMessage(chatId, numberedNews);

                // Небольшая пауза между сообщениями, чтобы избежать спама
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

        } catch (Exception e) {
            logger.error("Ошибка при поиске новостей: {}", e.getMessage(), e);
            sendMessage(chatId, "❌ Ошибка при поиске новостей\\. Попробуйте позже\\.");
        }
    }

    /**
     * Отправка статистики
     */
    private void sendStats(Long chatId) {
        List<String> stats = newsService.getCategoriesStats();

        if (stats.isEmpty()) {
            sendMessage(chatId, "📊 Статистика пока недоступна.");
            return;
        }

        StringBuilder statsText = new StringBuilder("📊 *Статистика по категориям:*\n\n");
        for (String stat : stats) {
            statsText.append("• ").append(escapeMarkdown(stat)).append("\n");
        }

        sendMessage(chatId, statsText.toString());
    }

    /**
     * Отправка краткого изложения последних новостей
     */
    private void sendNewsSummary(Long chatId) {
        sendMessage(chatId, "🤖 Генерирую краткое изложение последних новостей...");

        try {
            String summary = aiSummaryService.generateTodayNewsSummary();
            sendMessage(chatId, summary);
        } catch (Exception e) {
            logger.error("Ошибка при создании краткого изложения новостей", e);
            sendMessage(chatId, "❌ Произошла ошибка при создании краткого изложения. Попробуйте позже.");
        }
    }

    /**
     * Обновление новостей
     */
    private void updateNews(Long chatId) {
        sendMessage(chatId, "🔄 Начинаю обновление новостей...");

        // Запускаем парсинг в отдельном потоке
        new Thread(() -> {
            try {
                parsingService.parseAllSources();
                sendMessage(chatId, "✅ Новости успешно обновлены!");
            } catch (Exception e) {
                logger.error("Ошибка при обновлении новостей: {}", e.getMessage(), e);
                sendMessage(chatId, "❌ Ошибка при обновлении новостей. Попробуйте позже.");
            }
        }).start();
    }

    /**
     * Отправка сообщения о неизвестной команде
     */
    private void sendUnknownCommandMessage(Long chatId) {
        sendMessage(chatId, "❓ Неизвестная команда. Используйте /help для просмотра доступных команд.");
    }

    /**
     * Создание основной клавиатуры
     */
    private ReplyKeyboardMarkup createMainKeyboard() {
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(false);

        List<KeyboardRow> keyboard = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton("📰 Последние новости"));
        row1.add(new KeyboardButton("📂 Категории"));

        KeyboardRow row2 = new KeyboardRow();
        row2.add(new KeyboardButton("📊 Статистика"));
        row2.add(new KeyboardButton("🔥 Тренды"));

        KeyboardRow row3 = new KeyboardRow();
        row3.add(new KeyboardButton("👤 Персоны"));
        row3.add(new KeyboardButton("🔄 Обновить новости"));

        KeyboardRow row4 = new KeyboardRow();
        row4.add(new KeyboardButton("📋 Краткое изложение"));

        keyboard.add(row1);
        keyboard.add(row2);
        keyboard.add(row3);
        keyboard.add(row4);

        keyboardMarkup.setKeyboard(keyboard);
        return keyboardMarkup;
    }

    /**
     * Отправка простого текстового сообщения
     */
    private void sendMessage(Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(text);
        message.setParseMode("Markdown");

        try {
            execute(message);
        } catch (TelegramApiException e) {
            logger.error("Ошибка при отправке сообщения: {}", e.getMessage(), e);
        }
    }

    /**
     * Отправка меню экспорта
     */
    private void sendExportMenu(Long chatId) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        List<InlineKeyboardButton> row1 = new ArrayList<>();
        InlineKeyboardButton csvButton = new InlineKeyboardButton();
        csvButton.setText("📄 CSV");
        csvButton.setCallbackData("export_csv");

        InlineKeyboardButton jsonButton = new InlineKeyboardButton();
        jsonButton.setText("📊 JSON");
        jsonButton.setCallbackData("export_json");

        InlineKeyboardButton htmlButton = new InlineKeyboardButton();
        htmlButton.setText("🌐 HTML");
        htmlButton.setCallbackData("export_html");

        row1.add(csvButton);
        row1.add(jsonButton);
        row1.add(htmlButton);

        keyboard.add(row1);
        markup.setKeyboard(keyboard);

        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("📥 Выберите формат для экспорта новостей:");
        message.setReplyMarkup(markup);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            logger.error("Ошибка при отправке меню экспорта: {}", e.getMessage(), e);
        }
    }

    /**
     * Экспорт новостей
     */
    private void exportNews(Long chatId, String format) {
        List<News> allNews = newsService.getLatestNews(100); // Экспорт максимум 100 новостей

        if (allNews.isEmpty()) {
            sendMessage(chatId, "📥 Нет новостей для экспорта.");
            return;
        }

        String exportData;
        String filename;

        switch (format.toLowerCase()) {
            case "csv":
                exportData = exportService.exportToCsv(allNews);
                filename = "news_export.csv";
                break;
            case "json":
                exportData = exportService.exportToJson(allNews);
                filename = "news_export.json";
                break;
            case "html":
                exportData = exportService.exportToHtml(allNews);
                filename = "news_export.html";
                break;
            default:
                sendMessage(chatId, "❌ Неподдерживаемый формат экспорта: " + escapeMarkdown(format));
                return;
        }

        try {
            // Создаем файл из строки
            byte[] fileData = exportData.getBytes(StandardCharsets.UTF_8);
            InputStream fileStream = new ByteArrayInputStream(fileData);

            // Создаем и отправляем документ
            SendDocument sendDocument = new SendDocument();
            sendDocument.setChatId(chatId);
            sendDocument.setDocument(new InputFile(fileStream, filename));
            sendDocument.setCaption(String.format("📥 Экспорт готов! Файл %s содержит %d новостей.",
                    escapeMarkdown(filename), allNews.size()));
            sendDocument.setParseMode("Markdown");

            execute(sendDocument);

        } catch (Exception e) {
            logger.error("Ошибка при отправке файла экспорта: {}", e.getMessage(), e);
            sendMessage(chatId, "❌ Ошибка при создании файла экспорта. Попробуйте позже.");
        }
    }

    /**
     * Экранирование спецсимволов Markdown
     */
    private String escapeMarkdown(String text) {
        if (text == null)
            return "";
        return text.replace("_", "\\_")
                .replace("*", "\\*")
                .replace("[", "\\[")
                .replace("]", "\\]")
                .replace("(", "\\(")
                .replace(")", "\\)")
                .replace("~", "\\~")
                .replace("`", "\\`")
                .replace(">", "\\>")
                .replace("#", "\\#")
                .replace("+", "\\+")
                .replace("-", "\\-")
                .replace("=", "\\=")
                .replace("|", "\\|")
                .replace("{", "\\{")
                .replace("}", "\\}")
                .replace(".", "\\.")
                .replace("!", "\\!");
    }

    /**
     * Отправка трендовых тем
     */
    private void sendTrends(Long chatId) {
        try {
            List<String> trends = analyticsService.getTrendingTopics(10);

            if (trends.isEmpty()) {
                sendMessage(chatId, "🔥 Трендовые темы пока не найдены.");
                return;
            }

            // Отправляем список трендовых тем
            StringBuilder trendsText = new StringBuilder("🔥 **Трендовые темы:**\n\n");
            for (int i = 0; i < trends.size(); i++) {
                trendsText.append(String.format("%d. %s\n", i + 1, trends.get(i)));
            }
            sendMessage(chatId, trendsText.toString());

            // Добавляем кнопки для выбора обычного просмотра или AI-анализа
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

            List<InlineKeyboardButton> row = new ArrayList<>();
            InlineKeyboardButton analysisButton = new InlineKeyboardButton();
            analysisButton.setText("📊 Получить AI-анализ трендов");
            analysisButton.setCallbackData("trends_analysis");
            row.add(analysisButton);
            keyboard.add(row);

            markup.setKeyboard(keyboard);

            SendMessage message = new SendMessage();
            message.setChatId(chatId);
            message.setText("💡 Вы можете получить подробный AI-анализ этих трендов:");
            message.setReplyMarkup(markup);

            try {
                execute(message);
            } catch (TelegramApiException e) {
                logger.error("Ошибка при отправке кнопки анализа трендов: {}", e.getMessage(), e);
            }

        } catch (Exception e) {
            logger.error("Ошибка при получении трендов: {}", e.getMessage(), e);
            sendMessage(chatId, "❌ Ошибка при получении трендов. Попробуйте позже.");
        }
    }

    /**
     * Отправка AI-анализа трендовых тем
     */
    private void sendTrendsAnalysis(Long chatId) {
        sendMessage(chatId, "🤖 Генерирую AI-анализ трендовых тем...");

        try {
            List<String> trends = analyticsService.getTrendingTopics(10);
            if (trends.isEmpty()) {
                sendMessage(chatId, "🔥 Трендовые темы пока не найдены.");
                return;
            }

            String analysis = aiSummaryService.generateTrendsAnalysis(trends);
            sendMessage(chatId, analysis);
        } catch (Exception e) {
            logger.error("Ошибка при создании AI-анализа трендов: {}", e.getMessage(), e);
            sendMessage(chatId, "❌ Произошла ошибка при создании AI-анализа трендов. Попробуйте позже.");
        }
    }

    /**
     * Отправка AI-сводки по категории
     */
    private void sendCategorySummary(Long chatId, String categoryName) {
        sendMessage(chatId, String.format("🤖 Генерирую AI-сводку по категории \"%s\"...", categoryName));

        try {
            String summary = aiSummaryService.generateCategorySummary(categoryName);
            sendMessage(chatId, summary);
        } catch (Exception e) {
            logger.error("Ошибка при создании AI-сводки по категории: {}", e.getMessage(), e);
            sendMessage(chatId, "❌ Произошла ошибка при создании AI-сводки по категории. Попробуйте позже.");
        }
    }

    /**
     * Отправка информации о персонах
     */
    private void sendPersons(Long chatId) {
        try {
            Map<String, Integer> persons = analyticsService.getPersonMentions(20);

            if (persons.isEmpty()) {
                sendMessage(chatId, "👤 Персоны в новостях пока не найдены.");
                return;
            }

            StringBuilder personsText = new StringBuilder("👤 **Упоминаемые персоны:**\n\n");
            int rank = 1;
            for (Map.Entry<String, Integer> entry : persons.entrySet()) {
                String escapedName = escapeMarkdown(entry.getKey());
                personsText.append(String.format("%d. %s (%d упоминаний)\n",
                        rank++, escapedName, entry.getValue()));
            }

            sendMessage(chatId, personsText.toString());
        } catch (Exception e) {
            logger.error("Ошибка при получении списка персон: {}", e.getMessage(), e);
            sendMessage(chatId, "❌ Ошибка при получении списка персон. Попробуйте позже.");
        }
    }

    /**
     * Отправка информации об активности системы
     */
    private void sendActivity(Long chatId) {
        sendMessage(chatId, "⚡ Функция просмотра активности системы временно недоступна.");
    }

    @Override
    public String getBotUsername() {
        return config.getBotUsername();
    }
}