package ru.news.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.news.exception.NewsParsingException;
import ru.news.model.News;
import ru.news.parser.NewsParser;
import ru.news.parser.RiaNewsParser;
import ru.news.parser.RamblerNewsParser;
import ru.news.parser.MailRuNewsParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Сервис для парсинга новостей из различных источников
 */
public class NewsParsingService {
    private static final Logger logger = LoggerFactory.getLogger(NewsParsingService.class);
    private final NewsService newsService;
    private final List<NewsParser> parsers;
    private final ExecutorService executorService;
    private static final int SHUTDOWN_TIMEOUT_SECONDS = 30;

    public NewsParsingService(NewsService newsService) {
        this.newsService = newsService;
        this.parsers = initializeParsers();
        this.executorService = Executors.newFixedThreadPool(Math.min(parsers.size(), Runtime.getRuntime().availableProcessors()));
    }

    /**
     * Инициализация парсеров
     */
    private List<NewsParser> initializeParsers() {
        List<NewsParser> parserList = new ArrayList<>();
        parserList.add(new RiaNewsParser());
        parserList.add(new RamblerNewsParser());
        parserList.add(new MailRuNewsParser());
        return Collections.unmodifiableList(parserList);
    }

    /**
     * Парсинг новостей из всех источников
     */
    public void parseAllSources() {
        if (executorService.isShutdown()) {
            logger.warn("Невозможно выполнить парсинг - сервис парсинга уже остановлен");
            return;
        }
        
        logger.info("Начинаем парсинг новостей из всех источников");

        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (NewsParser parser : parsers) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    parseFromSource(parser);
                } catch (Exception e) {
                    logger.error("Ошибка при парсинге из источника {}: {}",
                            parser.getSourceName(), e.getMessage(), e);
                }
            }, executorService);

            futures.add(future);
        }

        // Ждем завершения всех задач
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .thenRun(() -> logger.info("Парсинг из всех источников завершен"))
                    .join();
        } catch (Exception e) {
            logger.error("Ошибка при ожидании завершения задач парсинга: {}", e.getMessage(), e);
        }
    }

    /**
     * Парсинг новостей из конкретного источника
     */
    private void parseFromSource(NewsParser parser) {
        try {
            if (parser == null) {
                logger.warn("Попытка парсинга с null парсером");
                return;
            }
            
            logger.info("Парсинг новостей из источника: {}", parser.getSourceName());

            List<News> newsList = parser.parseNews();
            
            if (newsList == null || newsList.isEmpty()) {
                logger.info("Нет новых новостей из источника: {}", parser.getSourceName());
                return;
            }

            int savedCount = 0;
            for (News news : newsList) {
                if (news != null) {
                    newsService.saveNews(news);
                    savedCount++;
                }
            }

            logger.info("Обработано {} новостей из источника: {}",
                    savedCount, parser.getSourceName());

        } catch (NewsParsingException e) {
            logger.error("Ошибка при парсинге новостей из {}: {}",
                    parser.getSourceName(), e.getMessage(), e);
        }
    }

    /**
     * Получение списка доступных источников
     */
    public List<String> getAvailableSources() {
        return parsers.stream()
                .map(NewsParser::getSourceName)
                .toList();
    }

    /**
     * Закрытие сервиса
     */
    public void shutdown() {
        if (executorService != null && !executorService.isShutdown()) {
            try {
                logger.info("Инициирована остановка сервиса парсинга новостей");
                executorService.shutdown();
                
                // Ожидание завершения выполняющихся задач
                if (!executorService.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    logger.warn("Некоторые задачи парсинга не завершились за отведенное время. Принудительная остановка.");
                    executorService.shutdownNow();
                    
                    // Повторное ожидание после shutdownNow
                    if (!executorService.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS/2, TimeUnit.SECONDS)) {
                        logger.error("Не удалось полностью остановить сервис парсинга новостей");
                    }
                }
                
                logger.info("Сервис парсинга новостей успешно остановлен");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("Прерывание во время остановки сервиса парсинга: {}", e.getMessage(), e);
                executorService.shutdownNow();
            }
        }
    }
    
    /**
     * Проверка статуса сервиса
     * @return true если сервис запущен и готов к работе
     */
    public boolean isRunning() {
        return executorService != null && !executorService.isShutdown() && !executorService.isTerminated();
    }
}