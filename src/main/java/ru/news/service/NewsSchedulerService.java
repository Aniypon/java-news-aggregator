package ru.news.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.news.config.AppConfig;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Сервис для автоматического обновления новостей по расписанию
 */
public class NewsSchedulerService {
    private static final Logger logger = LoggerFactory.getLogger(NewsSchedulerService.class);

    private final NewsParsingService parsingService;
    private final AppConfig config;
    private final ScheduledExecutorService scheduler;
    
    private static final int SHUTDOWN_TIMEOUT_SECONDS = 30;
    private static final int MAX_RETRY_COUNT = 3;
    private static final int CLEANUP_INTERVAL_HOURS = 24;
    private static final int NEWS_CLEANUP_AGE_DAYS = 30;
    
    // Храним ссылки на запланированные задачи для возможности их отмены
    private ScheduledFuture<?> updateTask;
    private ScheduledFuture<?> cleanupTask;
    
    // Счетчик неудачных попыток обновления
    private final AtomicInteger failedUpdateAttempts = new AtomicInteger(0);

    public NewsSchedulerService(NewsParsingService parsingService, AppConfig config) {
        this.parsingService = parsingService;
        this.config = config;
        this.scheduler = Executors.newScheduledThreadPool(2);
    }

    /**
     * Запуск автоматического обновления новостей
     */
    public void startScheduledUpdates() {
        if (scheduler.isShutdown()) {
            logger.warn("Невозможно запустить планировщик - сервис уже остановлен");
            return;
        }
        
        // Проверка запущен ли уже планировщик
        if (isUpdateTaskRunning()) {
            logger.info("Автоматическое обновление новостей уже запущено");
            return;
        }
        
        long intervalMinutes = Math.max(5, config.getParsingIntervalMinutes()); // Минимальный интервал 5 минут

        logger.info("Запуск автоматического обновления новостей каждые {} минут", intervalMinutes);

        // Планируем регулярное обновление
        updateTask = scheduler.scheduleAtFixedRate(
                this::safeUpdateNews,
                0, // Запуск сразу
                intervalMinutes,
                TimeUnit.MINUTES);

        // Планируем очистку старых новостей раз в день
        cleanupTask = scheduler.scheduleAtFixedRate(
                this::safeCleanupOldNews,
                1, // Запуск через час после старта
                CLEANUP_INTERVAL_HOURS,
                TimeUnit.HOURS);
    }

    /**
     * Проверяет, запущена ли задача обновления
     */
    private boolean isUpdateTaskRunning() {
        return updateTask != null && !updateTask.isDone() && !updateTask.isCancelled();
    }

    /**
     * "Безопасный" метод для обновления новостей с обработкой исключений
     */
    private void safeUpdateNews() {
        try {
            updateNews();
            // Сбрасываем счетчик неудачных попыток при успешном обновлении
            failedUpdateAttempts.set(0);
        } catch (Exception e) {
            int attempts = failedUpdateAttempts.incrementAndGet();
            logger.error("Ошибка при плановом обновлении новостей (попытка {}): {}", 
                attempts, e.getMessage(), e);
                
            // Если превысили лимит неудачных попыток, делаем паузу
            if (attempts >= MAX_RETRY_COUNT) {
                logger.warn("Превышен лимит неудачных попыток обновления ({}). Временная приостановка обновлений", 
                    MAX_RETRY_COUNT);
                
                // Перепланируем задачу с большим интервалом для временной приостановки
                rescheduleUpdateTaskAfterFailure();
            }
        }
    }
    
    /**
     * Перепланирование задачи обновления после серии неудач
     */
    private void rescheduleUpdateTaskAfterFailure() {
        if (updateTask != null) {
            updateTask.cancel(false);
            
            // Запланировать следующее обновление с увеличенным интервалом
            long recoveryIntervalMinutes = config.getParsingIntervalMinutes() * 2;
            logger.info("Перепланирование обновления через {} минут", recoveryIntervalMinutes);
            
            updateTask = scheduler.schedule(() -> {
                failedUpdateAttempts.set(0); // Сброс счетчика
                logger.info("Возобновление регулярных обновлений новостей");
                startScheduledUpdates(); // Перезапуск регулярных обновлений
            }, recoveryIntervalMinutes, TimeUnit.MINUTES);
        }
    }

    /**
     * "Безопасный" метод для очистки старых новостей с обработкой исключений
     */
    private void safeCleanupOldNews() {
        try {
            cleanupOldNews();
        } catch (Exception e) {
            logger.error("Ошибка при очистке старых новостей: {}", e.getMessage(), e);
        }
    }

    /**
     * Остановка планировщика
     */
    public void shutdown() {
        logger.info("Остановка планировщика обновления новостей");
        
        // Отменяем запланированные задачи
        if (updateTask != null) {
            updateTask.cancel(false);
        }
        if (cleanupTask != null) {
            cleanupTask.cancel(false);
        }
        
        // Останавливаем планировщик
        scheduler.shutdown();

        try {
            if (!scheduler.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                logger.warn("Не все задачи завершены в отведенное время. Принудительное завершение.");
                scheduler.shutdownNow();
                
                if (!scheduler.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS / 2, TimeUnit.SECONDS)) {
                    logger.error("Не удалось полностью остановить планировщик");
                }
            }
        } catch (InterruptedException e) {
            logger.error("Прервано ожидание остановки планировщика: {}", e.getMessage());
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Ручное обновление новостей
     */
    public void manualUpdate() {
        logger.info("Запуск ручного обновления новостей");
        try {
            updateNews();
        } catch (Exception e) {
            logger.error("Ошибка при ручном обновлении новостей: {}", e.getMessage(), e);
            throw new RuntimeException("Ошибка при обновлении новостей", e);
        }
    }

    /**
     * Проверка активности планировщика
     * 
     * @return true, если планировщик активен и не завершен
     */
    public boolean isRunning() {
        return scheduler != null && !scheduler.isShutdown() && !scheduler.isTerminated();
    }

    /**
     * Метод для обновления новостей
     */
    private void updateNews() {
        logger.info("Начало обновления новостей...");

        // Проверка работоспособности парсинг-сервиса
        if (parsingService == null || !parsingService.isRunning()) {
            logger.error("Сервис парсинга не инициализирован или остановлен");
            return;
        }

        // Запускаем парсинг новостей
        parsingService.parseAllSources();

        logger.info("Обновление новостей завершено успешно в {}", LocalDateTime.now());
    }

    /**
     * Очистка старых новостей
     */
    private void cleanupOldNews() {
        LocalDateTime cutoffDate = LocalDateTime.now().minus(NEWS_CLEANUP_AGE_DAYS, ChronoUnit.DAYS);
        logger.info("Начало очистки новостей старше {}", cutoffDate);

        // Здесь можно добавить логику очистки старых новостей
        // например, удаление новостей старше определенного периода
        
        // TODO: Реализовать удаление устаревших новостей через DatabaseManager
        // databaseManager.deleteNewsOlderThan(cutoffDate);

        logger.info("Очистка старых новостей завершена");
    }

    /**
     * Получение статуса планировщика
     */
    public String getSchedulerStatus() {
        if (scheduler.isShutdown()) {
            return "Остановлен";
        } else if (scheduler.isTerminated()) {
            return "Завершен";
        } else if (isUpdateTaskRunning()) {
            return "Запущен (обновление каждые " + config.getParsingIntervalMinutes() + " мин)";
        } else {
            return "Инициализирован, но не запущен";
        }
    }
    
    /**
     * Получение времени следующего запланированного обновления
     */
    public LocalDateTime getNextScheduledUpdateTime() {
        if (updateTask == null || updateTask.isDone() || updateTask.isCancelled()) {
            return null;
        }
        
        long delayInSeconds = updateTask.getDelay(TimeUnit.SECONDS);
        return LocalDateTime.now().plusSeconds(delayInSeconds);
    }
}
