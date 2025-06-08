package ru.news.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import ru.news.config.AppConfig;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NewsSchedulerServiceTest {

    @Mock
    private NewsParsingService parsingService;

    @Mock
    private AppConfig config;

    private NewsSchedulerService schedulerService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(config.getParsingIntervalMinutes()).thenReturn(30);
        schedulerService = new NewsSchedulerService(parsingService, config);
    }

    @Test
    void testIsRunning() {
        // При создании сервиса планировщик уже инициализирован и активен
        assertTrue(schedulerService.isRunning());

        // После остановки планировщик должен быть неактивен
        schedulerService.shutdown();
        assertFalse(schedulerService.isRunning());
    }

    @Test
    void testGetSchedulerStatus() {
        // Проверяем статус планировщика после создания
        String initialStatus = schedulerService.getSchedulerStatus();
        assertNotNull(initialStatus);

        // После запуска статус должен измениться
        schedulerService.startScheduledUpdates();
        String runningStatus = schedulerService.getSchedulerStatus();
        assertNotNull(runningStatus);
        assertTrue(runningStatus.contains("Запущен"));

        // После остановки статус должен снова измениться
        schedulerService.shutdown();
        String stoppedStatus = schedulerService.getSchedulerStatus();
        assertNotNull(stoppedStatus);
        assertFalse(stoppedStatus.contains("Запущен"));
    }

    @Test
    void testManualUpdate() {
        // Настраиваем мок на возвращение true для метода isRunning()
        when(parsingService.isRunning()).thenReturn(true);
        
        // Проверяем, что метод не выбрасывает исключений и вызывает парсер
        assertDoesNotThrow(() -> schedulerService.manualUpdate());

        // Проверяем, что парсер был вызван
        verify(parsingService, times(1)).parseAllSources();
    }

    @Test
    void testStartAndShutdown() {
        // Запускаем планировщик
        assertDoesNotThrow(() -> schedulerService.startScheduledUpdates());

        // Проверяем, что планировщик запущен
        assertTrue(schedulerService.isRunning());

        // Останавливаем планировщик
        assertDoesNotThrow(() -> schedulerService.shutdown());

        // Проверяем, что планировщик остановлен
        assertFalse(schedulerService.isRunning());
    }
}
