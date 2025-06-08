package ru.news.bot;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.news.config.AppConfig;
import ru.news.model.NewsCategory;
import ru.news.model.News;
import ru.news.model.UserSubscription;
import ru.news.service.AnalyticsService;
import ru.news.service.AiSummaryService;
import ru.news.service.NewsParsingService;
import ru.news.service.NewsSchedulerService;
import ru.news.service.NewsService;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NewsBotTest {

    @Mock
    private NewsService newsService;
    
    @Mock
    private NewsParsingService parsingService;
    
    @Mock
    private AnalyticsService analyticsService;
    
    @Mock
    private NewsSchedulerService schedulerService;
    
    @Mock
    private AppConfig config;
    
    @Mock
    private AiSummaryService aiSummaryService;
    
    private NewsBot newsBot;
    
    @BeforeEach
    void setUp() throws TelegramApiException {
        MockitoAnnotations.openMocks(this);
        
        // Настраиваем конфигурацию
        when(config.getBotToken()).thenReturn("test_token");
        when(config.getBotUsername()).thenReturn("test_bot");
        
        // Создаем NewsBot с моками
        newsBot = spy(new NewsBot(newsService, parsingService, analyticsService, schedulerService, config));
        
        // Устанавливаем мок для AiSummaryService через рефлексию
        try {
            java.lang.reflect.Field field = NewsBot.class.getDeclaredField("aiSummaryService");
            field.setAccessible(true);
            field.set(newsBot, aiSummaryService);
        } catch (Exception e) {
            fail("Ошибка при настройке мока для AiSummaryService: " + e.getMessage());
        }
        
        // Настраиваем мок для метода execute, который возвращает Message
        // Необходимо настроить ответ на любой SendMessage, чтобы избежать ошибки "Parameter method can not be null"
        Message mockMessage = mock(Message.class);
        doReturn(mockMessage).when(newsBot).execute(any(SendMessage.class));
    }
    
    @Test
    void getBotUsername_shouldReturnCorrectName() {
        assertEquals("test_bot", newsBot.getBotUsername());
    }
    
    @Test
    void getBotToken_shouldReturnToken() {
        assertEquals("test_token", newsBot.getBotToken());
    }
    
    @Test
    void onUpdateReceived_shouldHandleSearchCommand() throws TelegramApiException {
        // Подготовка тестовых данных
        List<News> searchResults = new ArrayList<>();
        News news = new News();
        news.setTitle("Новость о криптовалютах");
        news.setUrl("https://example.com/crypto-news");
        news.setContent("Биткоин вырос в цене");
        news.setPublishedAt(LocalDateTime.now());
        searchResults.add(news);
        
        when(newsService.searchNews(eq("криптовалют"), anyInt())).thenReturn(searchResults);
        
        // Подготовка обновления с командой /search криптовалют
        Update update = createUpdateWithMessage(123456789L, "/search криптовалют");
        
        // Выполнение метода
        newsBot.onUpdateReceived(update);
        
        // Проверка поиска новостей в сервисе
        verify(newsService).searchNews(eq("криптовалют"), anyInt());
        
        // Проверка отправки сообщения с результатами поиска
        ArgumentCaptor<SendMessage> messageCaptor = ArgumentCaptor.forClass(SendMessage.class);
        verify(newsBot, atLeastOnce()).execute(messageCaptor.capture());
        
        boolean messageContainsResults = false;
        for (SendMessage message : messageCaptor.getAllValues()) {
            if (message.getText().contains("Новость о криптовалютах") || 
                message.getText().toLowerCase().contains("результаты поиска")) {
                messageContainsResults = true;
                break;
            }
        }
        assertTrue(messageContainsResults, "Сообщение должно содержать результаты поиска");
    }
    
    @Test
    void onUpdateReceived_shouldHandleSummaryCommand() throws TelegramApiException {
        // Настраиваем AI-сервис для суммаризации
        String summaryResult = "Это сводка новостей за сегодня";
        when(aiSummaryService.generateTodayNewsSummary()).thenReturn(summaryResult);
        
        // Подготовка обновления с командой /summary
        Update update = createUpdateWithMessage(123456789L, "/summary");
        
        // Выполнение метода
        newsBot.onUpdateReceived(update);
        
        // Проверка получения сводки из сервиса
        verify(aiSummaryService).generateTodayNewsSummary();
        
        // Проверка отправки сообщения с результатами
        ArgumentCaptor<SendMessage> messageCaptor = ArgumentCaptor.forClass(SendMessage.class);
        verify(newsBot, atLeastOnce()).execute(messageCaptor.capture());
        
        boolean messageContainsSummary = false;
        for (SendMessage message : messageCaptor.getAllValues()) {
            if (message.getText().contains(summaryResult)) {
                messageContainsSummary = true;
                break;
            }
        }
        assertTrue(messageContainsSummary, "Сообщение должно содержать сводку новостей");
    }
    
    private Update createUpdateWithMessage(long chatId, String text) {
        // Создаем моки для Update, Message, Chat, User
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);
        User user = mock(User.class);
        
        // Устанавливаем возвращаемые значения
        when(update.hasMessage()).thenReturn(true);
        when(update.getMessage()).thenReturn(message);
        when(message.hasText()).thenReturn(true);
        when(message.getText()).thenReturn(text);
        when(message.getChat()).thenReturn(chat);
        when(message.getFrom()).thenReturn(user);
        when(chat.getId()).thenReturn(chatId);
        when(user.getId()).thenReturn(123L); // Дефолтный ID пользователя

        // Важно: SendMessage.setChatId() принимает строковое представление ID
        when(chat.getId()).thenReturn(chatId);
        
        return update;
    }
} 