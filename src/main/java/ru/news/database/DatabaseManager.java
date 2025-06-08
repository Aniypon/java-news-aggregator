package ru.news.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.news.exception.DatabaseException;
import ru.news.model.News;
import ru.news.model.NewsCategory;

import java.sql.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Менеджер базы данных для работы с SQLite
 */
public class DatabaseManager {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);
    private static final String DATABASE_URL = "jdbc:sqlite:news.db";
    
    // Кэш для подготовленных запросов
    private final Map<String, String> preparedQueries = new ConcurrentHashMap<>();
    
    // Статические SQL запросы для частого использования
    private static final String SQL_INSERT_NEWS = """
            INSERT INTO news (title, description, content, url, source, image_url, category, published_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
    
    private static final String SQL_GET_LATEST_NEWS = """
            SELECT id, title, description, content, url, source, image_url, category, published_at, created_at
            FROM news
            ORDER BY published_at DESC
            LIMIT ?
            """;
    
    private static final String SQL_CHECK_NEWS_EXISTS = """
            SELECT 1 FROM news WHERE url = ? LIMIT 1
            """;

    /**
     * Инициализация базы данных
     */
    public void initializeDatabase() throws DatabaseException {
        try (Connection connection = getConnection()) {
            createNewsTable(connection);
            createIndices(connection);
            normalizePublishedAtFormat(connection);
            logger.info("База данных успешно инициализирована");
        } catch (SQLException e) {
            throw new DatabaseException("Ошибка при инициализации базы данных", e);
        }
    }

    /**
     * Создание индексов для ускорения запросов
     */
    private void createIndices(Connection connection) throws SQLException {
        // Индекс для поля URL (уникальный идентификатор новости)
        createIndex(connection, "CREATE UNIQUE INDEX IF NOT EXISTS idx_news_url ON news(url)");
        
        // Индекс для публикации (для сортировки по дате)
        createIndex(connection, "CREATE INDEX IF NOT EXISTS idx_news_published_at ON news(published_at DESC)");
        
        // Индекс для категории (для выборки по категориям)
        createIndex(connection, "CREATE INDEX IF NOT EXISTS idx_news_category ON news(category)");
        
        // Индекс для источника новостей
        createIndex(connection, "CREATE INDEX IF NOT EXISTS idx_news_source ON news(source)");
        
        // Комбинированный индекс для поиска по дате и категории
        createIndex(connection, "CREATE INDEX IF NOT EXISTS idx_news_date_category ON news(published_at DESC, category)");
    }
    
    /**
     * Создание индекса с обработкой ошибок
     */
    private void createIndex(Connection connection, String indexSQL) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(indexSQL);
            logger.debug("Индекс создан или уже существует: {}", indexSQL);
        } catch (SQLException e) {
            logger.warn("Не удалось создать индекс: {}", e.getMessage());
            // Не бросаем исключение, чтобы не прерывать инициализацию БД
        }
    }

    /**
     * Нормализация формата published_at в существующих записях
     */
    private void normalizePublishedAtFormat(Connection connection) throws SQLException {
        // Проверяем, есть ли записи с некорректным форматом
        String checkSQL = "SELECT COUNT(*) as count FROM news WHERE published_at IS NOT NULL";
        try (Statement checkStatement = connection.createStatement();
                ResultSet rs = checkStatement.executeQuery(checkSQL)) {

            if (rs.next() && rs.getInt("count") > 0) {
                logger.info("Нормализация формата published_at для существующих записей...");

                // Обновляем записи, где published_at может быть в неправильном формате
                // Приводим к формату SQLite datetime
                String updateSQL = """
                        UPDATE news
                        SET published_at = datetime(published_at, 'localtime')
                        WHERE published_at IS NOT NULL
                        """;

                try (Statement updateStatement = connection.createStatement()) {
                    int updated = updateStatement.executeUpdate(updateSQL);
                    logger.info("Обновлено {} записей с нормализованным форматом published_at", updated);
                }
            }
        }
    }

    /**
     * Создание таблицы новостей
     */
    private void createNewsTable(Connection connection) throws SQLException {
        String createTableSQL = """
                CREATE TABLE IF NOT EXISTS news (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    title TEXT NOT NULL,
                    description TEXT,
                    content TEXT,
                    url TEXT UNIQUE NOT NULL,
                    source TEXT NOT NULL,
                    image_url TEXT,
                    category TEXT NOT NULL,
                    published_at DATETIME NOT NULL,
                    created_at DATETIME DEFAULT (datetime('now', 'localtime'))
                )
                """;

        try (Statement statement = connection.createStatement()) {
            statement.execute(createTableSQL);
            logger.debug("Таблица news создана или уже существует");
        }
    }

    /**
     * Сохранение новости в базу данных
     */
    public void saveNews(News news) throws DatabaseException {
        // Проверяем, что publishedAt не null
        if (news.getPublishedAt() == null) {
            logger.warn("Новость с null publishedAt, устанавливаем текущее время: {}", news.getTitle());
            news.setPublishedAt(LocalDateTime.now());
        }

        // Быстрая проверка на дубликат - защита от повторного сохранения
        if (newsExists(news.getUrl())) {
            logger.debug("Новость уже существует в базе: {}", news.getUrl());
            return;
        }

        String getIdSQL = "SELECT last_insert_rowid() as id";

        try (Connection connection = getConnection();
                PreparedStatement statement = connection.prepareStatement(SQL_INSERT_NEWS)) {

            statement.setString(1, news.getTitle());
            statement.setString(2, news.getDescription());
            statement.setString(3, news.getContent());
            statement.setString(4, news.getUrl());
            statement.setString(5, news.getSource());
            statement.setString(6, news.getImageUrl());
            statement.setString(7, news.getCategory().name());
            
            // Сохраняем publishedAt в формате ISO стандарта даты/времени
            String formattedDate = news.getPublishedAt().toString().replace("T", " ");
            statement.setString(8, formattedDate);

            int affectedRows = statement.executeUpdate();

            if (affectedRows > 0) {
                // Получаем ID последней вставленной записи через last_insert_rowid()
                try (Statement idStatement = connection.createStatement();
                        ResultSet rs = idStatement.executeQuery(getIdSQL)) {
                    if (rs.next()) {
                        news.setId(rs.getLong("id"));
                    }
                }
                logger.debug("Новость сохранена: {}", news.getTitle());
            }

        } catch (SQLException e) {
            if (e.getMessage().contains("UNIQUE constraint failed")) {
                logger.debug("Новость уже существует: {}", news.getUrl());
            } else {
                throw new DatabaseException("Ошибка при сохранении новости", e);
            }
        }
    }

    /**
     * Пакетное сохранение списка новостей
     */
    public void saveNewsBatch(List<News> newsList) throws DatabaseException {
        try (Connection connection = getConnection()) {
            // Отключаем автокоммит для использования транзакции
            connection.setAutoCommit(false);

            try (PreparedStatement statement = connection.prepareStatement(SQL_INSERT_NEWS);
                 PreparedStatement checkStatement = connection.prepareStatement(SQL_CHECK_NEWS_EXISTS)) {

                int saved = 0;
                for (News news : newsList) {
                    // Проверяем, что publishedAt не null
                    if (news.getPublishedAt() == null) {
                        news.setPublishedAt(LocalDateTime.now());
                    }

                    // Проверка на существование перед вставкой (часть транзакции)
                    checkStatement.setString(1, news.getUrl());
                    try (ResultSet rs = checkStatement.executeQuery()) {
                        if (rs.next()) {
                            // Новость уже существует, пропускаем
                            continue;
                        }
                    }

                    statement.setString(1, news.getTitle());
                    statement.setString(2, news.getDescription());
                    statement.setString(3, news.getContent());
                    statement.setString(4, news.getUrl());
                    statement.setString(5, news.getSource());
                    statement.setString(6, news.getImageUrl());
                    statement.setString(7, news.getCategory().name());
                    
                    // Сохраняем publishedAt в формате ISO стандарта даты/времени
                    String formattedDate = news.getPublishedAt().toString().replace("T", " ");
                    statement.setString(8, formattedDate);
                    
                    statement.addBatch();
                    saved++;

                    // Выполняем по 50 запросов в партии для оптимизации
                    if (saved % 50 == 0) {
                        statement.executeBatch();
                    }
                }

                // Выполняем оставшиеся запросы в пакете
                if (saved % 50 != 0) {
                    statement.executeBatch();
                }

                // Подтверждаем транзакцию
                connection.commit();
                logger.debug("Сохранено {} новостей из {}", saved, newsList.size());

            } catch (SQLException e) {
                // Откатываем транзакцию при ошибке
                connection.rollback();
                throw e;
            } finally {
                // Возвращаем автокоммит
                connection.setAutoCommit(true);
            }

        } catch (SQLException e) {
            throw new DatabaseException("Ошибка при пакетном сохранении новостей", e);
        }
    }

    /**
     * Получение последних новостей
     */
    public List<News> getLatestNews(int limit) throws DatabaseException {
        List<News> newsList = new ArrayList<>();

        try (Connection connection = getConnection();
                PreparedStatement statement = connection.prepareStatement(SQL_GET_LATEST_NEWS)) {

            statement.setInt(1, limit);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    newsList.add(mapResultSetToNews(resultSet));
                }
            }

        } catch (SQLException e) {
            throw new DatabaseException("Ошибка при получении новостей", e);
        }

        return newsList;
    }

    /**
     * Поиск новостей по ключевым словам
     */
    public List<News> searchNews(String keyword, int limit) throws DatabaseException {
        String searchSQL = """
                SELECT id, title, description, content, url, source, image_url, category, published_at, created_at
                FROM news
                WHERE title LIKE ? OR description LIKE ? OR content LIKE ?
                ORDER BY published_at DESC
                LIMIT ?
                """;

        List<News> newsList = new ArrayList<>();
        String searchPattern = "%" + keyword + "%";

        try (Connection connection = getConnection();
                PreparedStatement statement = connection.prepareStatement(searchSQL)) {

            statement.setString(1, searchPattern);
            statement.setString(2, searchPattern);
            statement.setString(3, searchPattern);
            statement.setInt(4, limit);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    newsList.add(mapResultSetToNews(resultSet));
                }
            }

        } catch (SQLException e) {
            throw new DatabaseException("Ошибка при поиске новостей", e);
        }

        return newsList;
    }

    /**
     * Получение новостей по категории
     */
    public List<News> getNewsByCategory(NewsCategory category, int limit) throws DatabaseException {
        String categorySQL = """
                SELECT id, title, description, content, url, source, image_url, category, published_at, created_at
                FROM news
                WHERE category = ?
                ORDER BY published_at DESC
                LIMIT ?
                """;

        List<News> newsList = new ArrayList<>();

        try (Connection connection = getConnection();
                PreparedStatement statement = connection.prepareStatement(categorySQL)) {

            statement.setString(1, category.name());
            statement.setInt(2, limit);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    newsList.add(mapResultSetToNews(resultSet));
                }
            }

        } catch (SQLException e) {
            throw new DatabaseException("Ошибка при получении новостей по категории", e);
        }

        return newsList;
    }

    /**
     * Получение статистики по категориям
     */
    public List<String> getCategoriesStats() throws DatabaseException {
        String statsSQL = """
                SELECT 
                    category, 
                    COUNT(*) as count
                FROM news
                GROUP BY category
                ORDER BY count DESC
                """;

        List<String> stats = new ArrayList<>();

        try (Connection connection = getConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(statsSQL)) {

            while (resultSet.next()) {
                String category = resultSet.getString("category");
                int count = resultSet.getInt("count");
                stats.add(String.format("%s: %d", category, count));
            }

        } catch (SQLException e) {
            throw new DatabaseException("Ошибка при получении статистики по категориям", e);
        }

        return stats;
    }

    /**
     * Проверка существования новости по URL
     */
    public boolean newsExists(String url) throws DatabaseException {
        try (Connection connection = getConnection();
                PreparedStatement statement = connection.prepareStatement(SQL_CHECK_NEWS_EXISTS)) {

            statement.setString(1, url);

            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }

        } catch (SQLException e) {
            throw new DatabaseException("Ошибка при проверке существования новости", e);
        }
    }

    /**
     * Получение новостей с указанной даты
     */
    public List<News> getNewsSince(LocalDateTime since) throws DatabaseException {
        String sinceSQL = """
                SELECT id, title, description, content, url, source, image_url, category, published_at, created_at
                FROM news
                WHERE published_at >= ?
                ORDER BY published_at DESC
                """;

        List<News> newsList = new ArrayList<>();

        try (Connection connection = getConnection();
                PreparedStatement statement = connection.prepareStatement(sinceSQL)) {

            String formattedDate = since.toString().replace("T", " ");
            statement.setString(1, formattedDate);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    newsList.add(mapResultSetToNews(resultSet));
                }
            }

        } catch (SQLException e) {
            throw new DatabaseException("Ошибка при получении новостей с указанной даты", e);
        }

        return newsList;
    }

    /**
     * Получение новостей за указанный день
     */
    public List<News> getNewsForDate(LocalDateTime date) throws DatabaseException {
        // Форматируем даты начала и конца дня в стандартном формате
        String startOfDay = date.toLocalDate().atStartOfDay().toString().replace("T", " ");
        String endOfDay = date.toLocalDate().plusDays(1).atStartOfDay().toString().replace("T", " ");

        String forDateSQL = """
                SELECT id, title, description, content, url, source, image_url, category, published_at, created_at
                FROM news
                WHERE published_at >= ? AND published_at < ?
                ORDER BY published_at DESC
                """;

        List<News> newsList = new ArrayList<>();

        try (Connection connection = getConnection();
                PreparedStatement statement = connection.prepareStatement(forDateSQL)) {

            statement.setString(1, startOfDay);
            statement.setString(2, endOfDay);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    newsList.add(mapResultSetToNews(resultSet));
                }
            }

        } catch (SQLException e) {
            throw new DatabaseException("Ошибка при получении новостей за указанный день", e);
        }

        return newsList;
    }

    /**
     * Получение новостей по дате
     */
    public List<News> getNewsByDate(LocalDate date) throws DatabaseException {
        // Форматируем даты начала и конца дня в стандартном формате
        String startOfDay = date.atStartOfDay().toString().replace("T", " ");
        String endOfDay = date.plusDays(1).atStartOfDay().toString().replace("T", " ");

        String byDateSQL = """
                SELECT id, title, description, content, url, source, image_url, category, published_at, created_at
                FROM news
                WHERE published_at >= ? AND published_at < ?
                ORDER BY published_at DESC
                """;

        List<News> newsList = new ArrayList<>();

        try (Connection connection = getConnection();
                PreparedStatement statement = connection.prepareStatement(byDateSQL)) {

            statement.setString(1, startOfDay);
            statement.setString(2, endOfDay);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    newsList.add(mapResultSetToNews(resultSet));
                }
            }

        } catch (SQLException e) {
            throw new DatabaseException("Ошибка при получении новостей по дате", e);
        }

        return newsList;
    }

    /**
     * Получение общего количества новостей
     */
    public int getTotalNewsCount() throws DatabaseException {
        String countSQL = "SELECT COUNT(*) as count FROM news";

        try (Connection connection = getConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(countSQL)) {

            if (resultSet.next()) {
                return resultSet.getInt("count");
            }

        } catch (SQLException e) {
            throw new DatabaseException("Ошибка при получении общего количества новостей", e);
        }

        return 0;
    }

    /**
     * Получение статистики по источникам
     */
    public Map<String, Integer> getSourceStatistics() throws DatabaseException {
        String statsSQL = """
                SELECT 
                    source, 
                    COUNT(*) as count
                FROM news
                GROUP BY source
                ORDER BY count DESC
                """;

        Map<String, Integer> stats = new HashMap<>();

        try (Connection connection = getConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(statsSQL)) {

            while (resultSet.next()) {
                String source = resultSet.getString("source");
                int count = resultSet.getInt("count");
                stats.put(source, count);
            }

        } catch (SQLException e) {
            throw new DatabaseException("Ошибка при получении статистики по источникам", e);
        }

        return stats;
    }

    /**
     * Получение даты самой старой новости
     */
    public LocalDateTime getOldestNewsDate() throws DatabaseException {
        String oldestSQL = """
                SELECT published_at
                FROM news
                ORDER BY published_at ASC
                LIMIT 1
                """;

        try (Connection connection = getConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(oldestSQL)) {

            if (resultSet.next()) {
                String dateString = resultSet.getString("published_at");
                if (dateString.contains(" ")) {
                    return LocalDateTime.parse(dateString.replace(" ", "T"));
                } else {
                    return LocalDateTime.parse(dateString);
                }
            }

        } catch (SQLException | DateTimeParseException e) {
            throw new DatabaseException("Ошибка при получении даты самой старой новости", e);
        }

        return LocalDateTime.now();
    }

    /**
     * Получение даты самой новой новости
     */
    public LocalDateTime getNewestNewsDate() throws DatabaseException {
        String newestSQL = """
                SELECT published_at
                FROM news
                ORDER BY published_at DESC
                LIMIT 1
                """;

        try (Connection connection = getConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(newestSQL)) {

            if (resultSet.next()) {
                String dateString = resultSet.getString("published_at");
                if (dateString.contains(" ")) {
                    return LocalDateTime.parse(dateString.replace(" ", "T"));
                } else {
                    return LocalDateTime.parse(dateString);
                }
            }

        } catch (SQLException | DateTimeParseException e) {
            throw new DatabaseException("Ошибка при получении даты самой новой новости", e);
        }

        return LocalDateTime.now();
    }

    /**
     * Преобразование ResultSet в объект News
     */
    private News mapResultSetToNews(ResultSet resultSet) throws SQLException {
        long id = resultSet.getLong("id");
        String title = resultSet.getString("title");
        String description = resultSet.getString("description");
        String content = resultSet.getString("content");
        String url = resultSet.getString("url");
        String source = resultSet.getString("source");
        String imageUrl = resultSet.getString("image_url");
        String categoryStr = resultSet.getString("category");
        String publishedAtStr = resultSet.getString("published_at");
        String createdAtStr = resultSet.getString("created_at");

        LocalDateTime publishedAt;
        LocalDateTime createdAt;

        // Проверка формата даты и преобразование в LocalDateTime
        try {
            // Сначала пробуем стандартный формат базы данных (с пробелом)
            if (publishedAtStr.contains(" ")) {
                publishedAt = LocalDateTime.parse(publishedAtStr.replace(" ", "T"));
            } 
            // Если формат ISO с T, парсим напрямую
            else if (publishedAtStr.contains("T")) {
                publishedAt = LocalDateTime.parse(publishedAtStr);
            } 
            // Если это числовой формат (Unix timestamp в миллисекундах)
            else {
                try {
                    long timestamp = Long.parseLong(publishedAtStr);
                    publishedAt = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());
                } catch (NumberFormatException nfe) {
                    throw new SQLException("Невозможно распознать формат даты публикации: " + publishedAtStr);
                }
            }
        } catch (DateTimeParseException e) {
            throw new SQLException("Ошибка при парсинге даты публикации: " + publishedAtStr, e);
        }

        // Аналогичная логика для даты создания
        try {
            if (createdAtStr.contains(" ")) {
                createdAt = LocalDateTime.parse(createdAtStr.replace(" ", "T"));
            } 
            else if (createdAtStr.contains("T")) {
                createdAt = LocalDateTime.parse(createdAtStr);
            } 
            else {
                try {
                    long timestamp = Long.parseLong(createdAtStr);
                    createdAt = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());
                } catch (NumberFormatException nfe) {
                    throw new SQLException("Невозможно распознать формат даты создания: " + createdAtStr);
                }
            }
        } catch (DateTimeParseException e) {
            throw new SQLException("Ошибка при парсинге даты создания: " + createdAtStr, e);
        }

        NewsCategory category = NewsCategory.valueOf(categoryStr);

        News news = new News(title, description, content, url, source);
        news.setId(id);
        news.setCategory(category);
        news.setImageUrl(imageUrl);
        news.setPublishedAt(publishedAt);
        news.setCreatedAt(createdAt);

        return news;
    }

    /**
     * Получение соединения с базой данных
     */
    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DATABASE_URL);
    }

    /**
     * Очистка базы данных
     */
    public void clearDatabase() throws DatabaseException {
        String clearSQL = "DELETE FROM news";

        try (Connection connection = getConnection();
                Statement statement = connection.createStatement()) {

            statement.executeUpdate(clearSQL);
            logger.info("База данных очищена");

        } catch (SQLException e) {
            throw new DatabaseException("Ошибка при очистке базы данных", e);
        }
    }

    /**
     * Удаление базы данных
     */
    public void deleteDatabase() throws DatabaseException {
        try (Connection connection = getConnection();
                Statement statement = connection.createStatement()) {

            statement.executeUpdate("DROP TABLE IF EXISTS news");
            logger.info("Таблица новостей удалена");

        } catch (SQLException e) {
            throw new DatabaseException("Ошибка при удалении базы данных", e);
        }
    }
    
    /**
     * Удаление старых новостей (для очистки базы данных)
     */
    public int deleteOldNews(LocalDateTime olderThan) throws DatabaseException {
        String deleteSQL = """
                DELETE FROM news
                WHERE published_at < ?
                """;

        try (Connection connection = getConnection();
                PreparedStatement statement = connection.prepareStatement(deleteSQL)) {

            String formattedDate = olderThan.toString().replace("T", " ");
            statement.setString(1, formattedDate);
            int deleted = statement.executeUpdate();
            
            logger.info("Удалено {} старых новостей", deleted);
            return deleted;

        } catch (SQLException e) {
            throw new DatabaseException("Ошибка при удалении старых новостей", e);
        }
    }
    
    /**
     * Получение новостей по источнику
     */
    public List<News> getNewsBySource(String source, int limit) throws DatabaseException {
        String sourceSQL = """
                SELECT id, title, description, content, url, source, image_url, category, published_at, created_at
                FROM news
                WHERE source = ?
                ORDER BY published_at DESC
                LIMIT ?
                """;

        List<News> newsList = new ArrayList<>();

        try (Connection connection = getConnection();
                PreparedStatement statement = connection.prepareStatement(sourceSQL)) {

            statement.setString(1, source);
            statement.setInt(2, limit);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    newsList.add(mapResultSetToNews(resultSet));
                }
            }

        } catch (SQLException e) {
            throw new DatabaseException("Ошибка при получении новостей по источнику", e);
        }

        return newsList;
    }
    
    /**
     * Обновление категории новости
     */
    public void updateNewsCategory(long id, NewsCategory category) throws DatabaseException {
        String updateSQL = """
                UPDATE news
                SET category = ?
                WHERE id = ?
                """;

        try (Connection connection = getConnection();
                PreparedStatement statement = connection.prepareStatement(updateSQL)) {

            statement.setString(1, category.name());
            statement.setLong(2, id);

            int updated = statement.executeUpdate();
            logger.debug("Обновлена категория для {} новостей", updated);

        } catch (SQLException e) {
            throw new DatabaseException("Ошибка при обновлении категории новости", e);
        }
    }
}