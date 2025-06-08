package ru.news.exception;

/**
 * Исключение для ошибок парсинга новостей
 */
public class NewsParsingException extends Exception {

    public NewsParsingException(String message) {
        super(message);
    }

    public NewsParsingException(String message, Throwable cause) {
        super(message, cause);
    }
}
