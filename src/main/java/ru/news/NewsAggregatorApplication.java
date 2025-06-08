package ru.news;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import ru.news.bot.NewsBot;
import ru.news.config.AppConfig;
import ru.news.database.DatabaseManager;
import ru.news.service.AnalyticsService;
import ru.news.service.NewsParsingService;
import ru.news.service.NewsSchedulerService;
import ru.news.service.NewsService;

/**
 * Основной класс приложения для запуска Telegram бота агрегатора новостей
 */
public class NewsAggregatorApplication {
    private static final Logger logger = LoggerFactory.getLogger(NewsAggregatorApplication.class);

    public static void main(String[] args) {
        try {
            logger.info("Запуск приложения News Aggregator");

            // Инициализация конфигурации
            AppConfig config = new AppConfig();

            // Инициализация базы данных
            DatabaseManager databaseManager = new DatabaseManager();
            databaseManager.initializeDatabase();

            // Инициализация сервисов
            NewsService newsService = new NewsService(databaseManager);
            NewsParsingService parsingService = new NewsParsingService(newsService);
            AnalyticsService analyticsService = new AnalyticsService(databaseManager);
            NewsSchedulerService schedulerService = new NewsSchedulerService(parsingService, config);

            // Создание и регистрация бота
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            NewsBot newsBot = new NewsBot(newsService, parsingService, analyticsService,
                    schedulerService, config);

            botsApi.registerBot(newsBot);

            logger.info("Бот успешно запущен!");

            // Запуск автоматического планировщика обновления новостей
            schedulerService.startScheduledUpdates();

            // Добавляем graceful shutdown
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Завершение работы приложения...");
                schedulerService.shutdown();
            }));

        } catch (TelegramApiException e) {
            logger.error("Ошибка при регистрации бота: {}", e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Критическая ошибка при запуске приложения: {}", e.getMessage(), e);
        }
    }
}