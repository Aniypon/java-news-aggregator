package ru.news.parser;

import ru.news.exception.NewsParsingException;
import ru.news.model.News;

import java.util.List;

/**
 * Интерфейс для парсеров новостей
 */
public interface NewsParser {

    /**
     * Парсинг новостей с источника
     * 
     * @return список новостей
     * @throws NewsParsingException если произошла ошибка при парсинге
     */
    List<News> parseNews() throws NewsParsingException;

    /**
     * Получение названия источника
     * 
     * @return название источника
     */
    String getSourceName();
}