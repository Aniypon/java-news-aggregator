package ru.news.model;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Перечисление категорий новостей с улучшенной логикой определения
 */
public enum NewsCategory {
    POLITICS("Политика"),
    ECONOMY("Экономика"),
    SPORT("Спорт"),
    TECHNOLOGY("Технологии"),
    SCIENCE("Наука"),
    CULTURE("Культура"),
    HEALTH("Здоровье"),
    SOCIETY("Общество"),
    WORLD("Мир"),
    CRIME("Происшествия"),
    EDUCATION("Образование"),
    ENTERTAINMENT("Развлечения"),
    AUTO("Авто"),
    REAL_ESTATE("Недвижимость"),
    WEATHER("Погода"),
    OTHER("Другое");

    private final String displayName;

    // Карта с весами ключевых слов для каждой категории
    private static final Map<NewsCategory, Map<String, Integer>> CATEGORY_KEYWORDS = new HashMap<>();

    // Паттерны для более точного поиска
    private static final Map<NewsCategory, List<Pattern>> CATEGORY_PATTERNS = new HashMap<>();
    
    // Минимальный порог для предотвращения случайных категоризаций
    private static final int CATEGORY_THRESHOLD = 8;
    
    // Весовые коэффициенты для разных типов совпадений
    private static final int TITLE_MULTIPLIER = 2;
    private static final int PATTERN_TEXT_BONUS = 8;
    private static final int PATTERN_TITLE_BONUS = 12;

    static {
        initializeKeywords();
        initializePatterns();
    }

    NewsCategory(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    private static void initializeKeywords() {
        // Политика
        Map<String, Integer> politics = createKeywordMap(
            new String[] {"путин", "президент"}, 15,
            new String[] {"правительство", "министр", "дума", "парламент", "выборы", "санкции"}, 12,
            new String[] {"геополитика", "политик", "депутат", "губернатор", "мэр", "оппозиция", "кремль", "единая россия"}, 10,
            new String[] {"власть", "партия", "законопроект", "реформа", "администрация", "федерация", "кпрф", "лдпр", "митинг", "протест", "избирком", "цик"}, 8,
            new String[] {"закон", "указ", "постановление"}, 6
        );
        CATEGORY_KEYWORDS.put(POLITICS, politics);

        // Экономика
        Map<String, Integer> economy = createKeywordMap(
            new String[] {"экономика", "рубль"}, 15,
            new String[] {"бюджет", "налоги", "налог", "инфляция", "доллар", "нефть", "газ", "цб", "центробанк"}, 12,
            new String[] {"евро", "курс", "валюта", "банк", "кредит", "ипотека", "инвестиции", "биржа", "акции", "набиуллина", "газпром", "роснефть"}, 10,
            new String[] {"бизнес", "компания", "корпорация", "предприятие", "производство", "промышленность", "торговля", "экспорт", "импорт", "прибыль", "убытки", "финансы", "сбербанк", "втб"}, 8,
            new String[] {"компания"}, 6
        );
        CATEGORY_KEYWORDS.put(ECONOMY, economy);

        // Спорт
        Map<String, Integer> sport = createKeywordMap(
            new String[] {"спорт", "футбол", "хоккей", "олимпиада"}, 15,
            new String[] {"баскетбол", "теннис", "волейбол", "бокс", "чемпионат", "матч", "рпл", "кхл"}, 12,
            new String[] {"борьба", "турнир", "спортсмен", "тренер", "стадион", "финал", "зенит", "спартак", "цска", "биатлон", "фигурное катание"}, 10,
            new String[] {"игра", "команда", "арена", "полуфинал", "динамо", "локомотив", "лыжи", "гимнастика", "плавание"}, 8
        );
        CATEGORY_KEYWORDS.put(SPORT, sport);

        // Технологии
        Map<String, Integer> technology = createKeywordMap(
            new String[] {"технологии", "искусственный интеллект"}, 15,
            new String[] {"компьютер", "интернет", "нейросеть", "нейросети", "роботы"}, 12,
            new String[] {"робот", "смартфон", "айфон", "iphone", "android", "блокчейн", "криптовалюта", "биткоин", "программирование"}, 10,
            new String[] {"планшет", "ios", "приложение", "разработка", "инновации", "стартап", "софт", "apple", "google", "microsoft", "яндекс", "телеграм", "youtube", "it"}, 8,
            new String[] {"сайт", "платформа"}, 6
        );
        CATEGORY_KEYWORDS.put(TECHNOLOGY, technology);

        // Наука
        Map<String, Integer> science = createKeywordMap(
            new String[] {"наука"}, 15,
            new String[] {"исследование", "ученые", "открытие", "космос", "роскосмос", "нобелевская премия"}, 12,
            new String[] {"эксперимент", "лаборатория", "планета", "спутник", "ракета", "марс", "физика", "химия", "биология", "математика", "генетика", "днк", "академия наук"}, 10,
            new String[] {"научный", "луна", "университет", "институт", "ран"}, 8
        );
        CATEGORY_KEYWORDS.put(SCIENCE, science);

        // Заполнение остальных категорий аналогичным образом
        // Культура, Здоровье, Общество, Мир, Происшествия, Образование, Развлечения, Авто, Недвижимость, Погода
        // ... (код для остальных категорий такой же, но с соответствующими ключевыми словами)
        
        // Культура
        Map<String, Integer> culture = createKeywordMap(
            new String[] {"культура"}, 15,
            new String[] {"театр", "кино", "фильм", "музыка"}, 12,
            new String[] {"актер", "актриса", "режиссер", "концерт", "певец", "певица", "книга", "писатель", "литература", "выставка", "музей", "большой театр"}, 10,
            new String[] {"музыкант", "поэт", "галерея", "художник", "искусство", "мариинский", "эрмитаж", "третьяковка", "фестиваль", "сериал"}, 8
        );
        CATEGORY_KEYWORDS.put(CULTURE, culture);

        // Здоровье
        Map<String, Integer> health = createKeywordMap(
            new String[] {"здоровье", "медицина", "covid", "коронавирус"}, 15,
            new String[] {"врач", "доктор", "больница", "лечение", "вакцина", "эпидемия", "пандемия"}, 12,
            new String[] {"клиника", "болезнь", "заболевание", "операция", "лекарство", "прививка", "минздрав", "онкология"}, 10,
            new String[] {"препарат", "рак", "инфекция", "пациент", "симптомы", "диагноз"}, 8
        );
        CATEGORY_KEYWORDS.put(HEALTH, health);

        // Общество
        Map<String, Integer> society = createKeywordMap(
            new String[] {"общество"}, 15,
            new String[] {"социальный", "пенсия", "жкх"}, 12,
            new String[] {"пенсионер", "семья", "дети", "образование", "школа", "коммунальные услуги", "льготы", "пособие"}, 10,
            new String[] {"родители", "студент", "учитель", "тарифы", "молодежь"}, 8,
            new String[] {"граждане", "население", "жители"}, 6
        );
        CATEGORY_KEYWORDS.put(SOCIETY, society);

        // Мир
        Map<String, Integer> world = createKeywordMap(
            new String[] {"украина", "сво", "нато", "сша"}, 15,
            new String[] {"америка", "европа", "китай", "война"}, 12,
            new String[] {"индия", "япония", "германия", "франция", "британия", "англия", "конфликт"}, 10,
            new String[] {"италия", "испания", "польша", "турция", "иран", "израиль", "белоруссия", "казахстан", "международный", "мировой", "глобальный", "дипломатия", "саммит"}, 8,
            new String[] {"переговоры"}, 6
        );
        CATEGORY_KEYWORDS.put(WORLD, world);

        // Происшествия
        Map<String, Integer> crime = createKeywordMap(
            new String[] {"происшествие", "преступление", "убийство", "дтп", "пожар", "взрыв"}, 15,
            new String[] {"кража", "грабеж", "арест", "задержание", "авария", "катастрофа", "погиб"}, 12,
            new String[] {"полиция", "суд", "умер", "смерть", "мошенничество"}, 10,
            new String[] {"следствие", "расследование", "преступник", "подозреваемый"}, 8
        );
        CATEGORY_KEYWORDS.put(CRIME, crime);

        // Образование
        Map<String, Integer> education = createKeywordMap(
            new String[] {"образование"}, 15,
            new String[] {"школа", "университет", "вуз", "егэ"}, 12,
            new String[] {"институт", "учитель", "преподаватель", "студент", "ученик", "огэ"}, 10,
            new String[] {"экзамен", "урок", "обучение", "учеба", "диплом"}, 8,
            new String[] {"курс"}, 6
        );
        CATEGORY_KEYWORDS.put(EDUCATION, education);

        // Развлечения
        Map<String, Integer> entertainment = createKeywordMap(
            new String[] {"развлечения"}, 15,
            new String[] {"шоу", "телешоу"}, 12,
            new String[] {"реалити", "звезда", "знаменитость", "блогер"}, 10,
            new String[] {"ведущий", "инстаграм", "тикток", "игры", "видеоигры", "стример"}, 8,
            new String[] {"праздник"}, 6
        );
        CATEGORY_KEYWORDS.put(ENTERTAINMENT, entertainment);

        // Авто
        Map<String, Integer> auto = createKeywordMap(
            new String[] {"автомобиль", "автопром"}, 15,
            new String[] {"авто", "автосалон", "тест-драйв"}, 12,
            new String[] {"машина", "лада", "камаз", "уаз", "мерседес", "бмв", "ауди", "тойота"}, 10,
            new String[] {"газ"}, 8
        );
        CATEGORY_KEYWORDS.put(AUTO, auto);

        // Недвижимость
        Map<String, Integer> realEstate = createKeywordMap(
            new String[] {"недвижимость", "жилой комплекс", "новостройка"}, 15,
            new String[] {"квартира", "коттедж", "застройщик", "вторичное жилье", "риэлтор"}, 12,
            new String[] {"строительство"}, 10,
            new String[] {"дом", "офис", "аренда"}, 8
        );
        CATEGORY_KEYWORDS.put(REAL_ESTATE, realEstate);

        // Погода
        Map<String, Integer> weather = createKeywordMap(
            new String[] {"погода", "ураган", "прогноз погоды"}, 15,
            new String[] {"температура", "дождь", "снег", "буря", "циклон", "гроза", "град"}, 12,
            new String[] {"заморозки", "потепление", "похолодание"}, 10
        );
        CATEGORY_KEYWORDS.put(WEATHER, weather);
    }

    /**
     * Вспомогательный метод для создания карты ключевых слов с разными весами
     */
    private static Map<String, Integer> createKeywordMap(Object... keywordsAndWeights) {
        Map<String, Integer> result = new HashMap<>();
        int currentWeight = 0;
        List<String> currentWords = null;
        
        for (Object obj : keywordsAndWeights) {
            if (obj instanceof Integer) {
                currentWeight = (Integer) obj;
            } else if (obj instanceof String[]) {
                String[] words = (String[]) obj;
                for (String word : words) {
                    result.put(word, currentWeight);
                }
            }
        }
        
        return result;
    }

    private static void initializePatterns() {
        // Паттерны для более точного поиска
        Map<NewsCategory, List<Pattern>> patterns = new HashMap<>();

        // Политика
        patterns.put(POLITICS, Arrays.asList(
                Pattern.compile("\\b(президент|путин|правительство|министр)\\b", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\b(дума|парламент|депутат|губернатор)\\b", Pattern.CASE_INSENSITIVE)));

        // Экономика
        patterns.put(ECONOMY, Arrays.asList(
                Pattern.compile("\\b(рубл|доллар|евро)\\w*", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\b\\d+.*рубл", Pattern.CASE_INSENSITIVE),
                Pattern.compile("курс.*\\d+", Pattern.CASE_INSENSITIVE)));

        // Спорт
        patterns.put(SPORT, Arrays.asList(
                Pattern.compile("\\b\\d+:\\d+\\b", Pattern.CASE_INSENSITIVE), // счет матча
                Pattern.compile("\\b(футбол|хоккей|спорт)\\w*", Pattern.CASE_INSENSITIVE)));

        // Происшествия
        patterns.put(CRIME, Arrays.asList(
                Pattern.compile("\\b(погиб|умер|смерть)\\w*", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\b(дтп|авария|пожар|взрыв)\\w*", Pattern.CASE_INSENSITIVE)));

        CATEGORY_PATTERNS.putAll(patterns);
    }

    /**
     * Улучшенная категоризация с весовой системой
     */
    public static NewsCategory categorizeByContentAdvanced(String title, String content) {
        if (title == null && (content == null || content.isEmpty())) {
            return OTHER;
        }

        String text = composeText(title, content);
        String titleLower = title != null ? title.toLowerCase() : "";

        // Карта для подсчета весов категорий
        Map<NewsCategory, Integer> categoryScores = initializeScores();

        // Подсчет весов по ключевым словам
        calculateKeywordScores(text, titleLower, categoryScores);

        // Дополнительные бонусы по паттернам
        applyPatternMatches(text, titleLower, categoryScores);

        // Специальные правила для улучшения точности
        applySpecialRules(text, titleLower, categoryScores);

        // Находим категорию с наибольшим весом
        return determineTopCategory(categoryScores);
    }

    /**
     * Создает единый текст для анализа из заголовка и содержимого
     */
    private static String composeText(String title, String content) {
        StringBuilder result = new StringBuilder();
        if (title != null) {
            result.append(title.toLowerCase());
        }
        if (content != null && !content.isEmpty()) {
            if (result.length() > 0) {
                result.append(" ");
            }
            result.append(content.toLowerCase());
        }
        return result.toString();
    }

    /**
     * Инициализирует карту весов категорий
     */
    private static Map<NewsCategory, Integer> initializeScores() {
        Map<NewsCategory, Integer> scores = new HashMap<>();
        for (NewsCategory category : values()) {
            if (category != OTHER) {
                scores.put(category, 0);
            }
        }
        return scores;
    }

    /**
     * Рассчитывает баллы на основе ключевых слов
     */
    private static void calculateKeywordScores(String text, String titleLower, Map<NewsCategory, Integer> categoryScores) {
        for (Map.Entry<NewsCategory, Map<String, Integer>> categoryEntry : CATEGORY_KEYWORDS.entrySet()) {
            NewsCategory category = categoryEntry.getKey();
            Map<String, Integer> keywords = categoryEntry.getValue();

            int totalWeight = 0;
            for (Map.Entry<String, Integer> keywordEntry : keywords.entrySet()) {
                String keyword = keywordEntry.getKey();
                int weight = keywordEntry.getValue();

                // Подсчитываем количество вхождений в тексте
                int textCount = countOccurrences(text, keyword);
                // Дополнительный бонус за вхождения в заголовке
                int titleCount = countOccurrences(titleLower, keyword);

                if (textCount > 0 || titleCount > 0) {
                    // Бонус за заголовок (увеличенный вес)
                    int titleBonus = titleCount * weight * TITLE_MULTIPLIER;
                    // Вес за текст с убывающей отдачей для множественных вхождений
                    int textBonus = textCount > 0 ? (int) (weight * (1 + Math.log(textCount))) : 0;

                    totalWeight += titleBonus + textBonus;
                }
            }

            categoryScores.put(category, categoryScores.getOrDefault(category, 0) + totalWeight);
        }
    }

    /**
     * Добавляет бонусные баллы на основе совпадений регулярных выражений
     */
    private static void applyPatternMatches(String text, String title, Map<NewsCategory, Integer> categoryScores) {
        for (Map.Entry<NewsCategory, List<Pattern>> patternEntry : CATEGORY_PATTERNS.entrySet()) {
            NewsCategory category = patternEntry.getKey();
            List<Pattern> patterns = patternEntry.getValue();

            for (Pattern pattern : patterns) {
                if (pattern.matcher(text).find()) {
                    categoryScores.put(category, categoryScores.getOrDefault(category, 0) + PATTERN_TEXT_BONUS);
                }
                // Дополнительный бонус за совпадение в заголовке
                if (!title.isEmpty() && pattern.matcher(title).find()) {
                    categoryScores.put(category, categoryScores.getOrDefault(category, 0) + PATTERN_TITLE_BONUS);
                }
            }
        }
    }

    /**
     * Специальные правила для повышения точности категоризации
     */
    private static void applySpecialRules(String text, String title, Map<NewsCategory, Integer> categoryScores) {
        // Политические правила
        if (text.contains("путин") || text.contains("президент россии")) {
            addCategoryScore(categoryScores, POLITICS, 20);
        }

        // Экономические правила
        if (text.matches(".*\\b\\d+.*рубл.*") || text.matches(".*курс.*\\d+.*")) {
            addCategoryScore(categoryScores, ECONOMY, 15);
        }

        // Спортивные правила
        if (text.matches(".*\\d+:\\d+.*") || text.contains("счет")) {
            addCategoryScore(categoryScores, SPORT, 15);
        }

        // Происшествия
        if (containsAny(text, "погиб", "умер", "смерть", "убит", "убийство")) {
            addCategoryScore(categoryScores, CRIME, 25);
        }

        // ДТП и аварии
        if (containsAny(text, "дтп", "столкновение", "авария")) {
            addCategoryScore(categoryScores, CRIME, 20);
        }

        // Международные новости
        if ((text.contains("украин") || text.contains("сво")) &&
                (text.contains("военн") || text.contains("атак") || text.contains("бомб"))) {
            addCategoryScore(categoryScores, WORLD, 25);
        }

        // Технологии
        if (text.contains("искусственный интеллект") || text.contains("нейросет")) {
            addCategoryScore(categoryScores, TECHNOLOGY, 20);
        }

        // Здоровье и медицина
        if (text.contains("covid") || text.contains("коронавирус")) {
            addCategoryScore(categoryScores, HEALTH, 20);
        }

        // Экономика - компании
        if (containsAny(text, "сбербанк", "газпром", "роснефть")) {
            addCategoryScore(categoryScores, ECONOMY, 15);
        }

        // Приоритет заголовка над содержимым
        if (!title.isEmpty()) {
            // Если в заголовке есть четкие индикаторы, увеличиваем вес
            for (Map.Entry<NewsCategory, Integer> entry : categoryScores.entrySet()) {
                if (entry.getValue() > 0) {
                    // Небольшой бонус для категорий, найденных в заголовке
                    addCategoryScore(categoryScores, entry.getKey(), 5);
                }
            }
        }
    }

    /**
     * Безопасно добавляет баллы к категории
     */
    private static void addCategoryScore(Map<NewsCategory, Integer> scores, NewsCategory category, int points) {
        scores.put(category, scores.getOrDefault(category, 0) + points);
    }

    /**
     * Проверяет содержит ли текст хотя бы одно из указанных слов
     */
    private static boolean containsAny(String text, String... words) {
        if (text == null) return false;
        for (String word : words) {
            if (text.contains(word)) return true;
        }
        return false;
    }

    /**
     * Определяет наиболее подходящую категорию
     */
    private static NewsCategory determineTopCategory(Map<NewsCategory, Integer> categoryScores) {
        NewsCategory bestCategory = OTHER;
        int maxScore = 0;

        for (Map.Entry<NewsCategory, Integer> entry : categoryScores.entrySet()) {
            if (entry.getValue() > maxScore) {
                maxScore = entry.getValue();
                bestCategory = entry.getKey();
            }
        }

        // Устанавливаем минимальный порог для предотвращения случайных категоризаций
        return maxScore >= CATEGORY_THRESHOLD ? bestCategory : OTHER;
    }

    /**
     * Подсчет количества вхождений подстроки в тексте
     */
    private static int countOccurrences(String text, String substring) {
        if (text == null || substring == null || substring.isEmpty()) {
            return 0;
        }
        
        int count = 0;
        int lastIndex = 0;
        
        while ((lastIndex = text.indexOf(substring, lastIndex)) != -1) {
            count++;
            lastIndex += substring.length();
        }
        
        return count;
    }
}
