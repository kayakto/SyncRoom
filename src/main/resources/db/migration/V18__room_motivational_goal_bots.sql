CREATE TABLE room_bot (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id     UUID NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
    bot_type    VARCHAR(50) NOT NULL,
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    config      TEXT NOT NULL DEFAULT '{}',
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_room_bot_room_type ON room_bot(room_id, bot_type);
CREATE INDEX idx_room_bot_room_active ON room_bot(room_id, is_active);

CREATE TABLE bot_goal_template (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    context     VARCHAR(20) NOT NULL,  -- WORK, STUDY, SPORT, LEISURE
    text        VARCHAR(500) NOT NULL,
    category    VARCHAR(50),
    is_active   BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_bot_goal_template_context ON bot_goal_template(context, is_active);

INSERT INTO users (name, email, provider, avatar_url)
SELECT 'MotivBot', 'motivbot@syncroom.local', 'EMAIL', '/static/bots/motivbot.png'
WHERE NOT EXISTS (
    SELECT 1 FROM users WHERE email = 'motivbot@syncroom.local'
);

INSERT INTO bot_goal_template (context, text, category) VALUES
('STUDY', 'Прочитать 30 страниц учебника', 'learning'),
('STUDY', 'Решить 5 задач по математике', 'learning'),
('STUDY', 'Повторить конспект за прошлую неделю', 'learning'),
('STUDY', 'Сделать 20 карточек для повторения терминов', 'learning'),
('STUDY', 'Посмотреть 1 лекцию и выписать 5 ключевых идей', 'learning'),
('STUDY', 'Разобрать одну сложную тему по учебнику', 'learning'),
('STUDY', 'Решить 10 тестовых вопросов без подсказок', 'learning'),
('STUDY', 'Написать краткое резюме по теме на 1 страницу', 'learning'),
('STUDY', 'Потренировать практическое задание 40 минут', 'productivity'),
('STUDY', 'Подготовить 3 вопроса преподавателю', 'learning'),
('STUDY', 'Повторить формулы и объяснить их вслух', 'learning'),
('STUDY', 'Проверить домашнее задание и исправить ошибки', 'productivity'),
('STUDY', 'Собрать план подготовки к следующему занятию', 'productivity'),
('WORK', 'Закрыть 3 задачи из бэклога', 'productivity'),
('WORK', 'Провести code review для коллеги', 'productivity'),
('WORK', 'Обновить документацию проекта', 'productivity'),
('WORK', 'Разобрать входящие письма до Inbox Zero', 'productivity'),
('WORK', 'Подготовить план задач на завтра', 'productivity'),
('WORK', 'Закрыть одну самую приоритетную задачу дня', 'productivity'),
('WORK', 'Провести 30 минут без отвлечений над фичей', 'focus'),
('WORK', 'Сделать рефакторинг одного проблемного модуля', 'productivity'),
('WORK', 'Покрыть новый код unit-тестами', 'productivity'),
('WORK', 'Проверить баг-репорты и приоритизировать 5 штук', 'productivity'),
('WORK', 'Синхронизироваться с командой по статусу задач', 'motivation'),
('WORK', 'Подготовить заметки к следующему митингу', 'productivity'),
('WORK', 'Закрыть хотя бы одну задачу, которую откладывал', 'motivation'),
('SPORT', 'Сделать 50 отжиманий', 'health'),
('SPORT', 'Пробежать 3 км', 'health'),
('SPORT', 'Провести 15 минут растяжки', 'health'),
('SPORT', 'Сделать 3 подхода приседаний по 20 раз', 'health'),
('SPORT', 'Пройти 8000 шагов за день', 'health'),
('SPORT', 'Сделать 10 минут упражнений на корпус', 'health'),
('SPORT', 'Сделать разминку суставов 12 минут', 'wellbeing'),
('SPORT', 'Пробежать интервалы 20 минут', 'health'),
('SPORT', 'Сделать 30 берпи в комфортном темпе', 'health'),
('SPORT', 'Потренироваться с эспандером 15 минут', 'health'),
('SPORT', 'Сделать 25 минут йоги для восстановления', 'wellbeing'),
('SPORT', 'Потренировать баланс и координацию 10 минут', 'wellbeing'),
('SPORT', 'Выполнить легкую вечернюю заминку 10 минут', 'wellbeing'),
('LEISURE', 'Выпить 2 стакана воды в ближайший час', 'wellbeing'),
('LEISURE', 'Разобрать рабочий стол за 15 минут', 'routine'),
('LEISURE', 'Прочитать 10 страниц книги для себя', 'wellbeing'),
('LEISURE', 'Сделать паузу на дыхание 5 минут', 'wellbeing'),
('LEISURE', 'Записать 3 важных дела на сегодня', 'routine'),
('LEISURE', 'Убрать один небольшой участок дома', 'routine'),
('LEISURE', 'Ответить на одно отложенное личное сообщение', 'routine'),
('LEISURE', 'Сделать короткую прогулку 20 минут', 'wellbeing'),
('LEISURE', 'Отключить уведомления и сфокусироваться на 25 минут', 'motivation'),
('LEISURE', 'Подготовить вещи и план на завтра', 'routine'),
('LEISURE', 'Сделать цифровой детокс на 30 минут', 'wellbeing'),
('LEISURE', 'Записать 3 достижения за день', 'motivation'),
('LEISURE', 'Навести порядок в заметках или файлах 20 минут', 'routine');
