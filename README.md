# Сервисные акты

Система составления актов выполненных работ по ремонту техники: акты с уникальным номером, учёт времени инженеров, запчасти, технический отчёт с фото, выгрузка в Excel, письма и дашборд часов. Полное техническое задание — в [`SPEC.md`](SPEC.md), принятые решения — в [`docs/assumptions.md`](docs/assumptions.md) и [`docs/adr/`](docs/adr).

**Текущее состояние: этап 1 — каркас.** Все сервисы запускаются, проверяют токен Keycloak и применяют миграции; бизнес-функций пока нет.

## Что нужно установить

- Docker Desktop (Windows/macOS) или Docker Engine с плагином Compose (Linux), не меньше 8 ГБ памяти для Docker;
- JDK 21 (например, Eclipse Temurin) — только для сборки и запуска сервисов вне Docker;
- Node.js 22 — только для разработки фронтенда вне Docker.

Maven устанавливать не нужно: в репозитории есть Maven Wrapper (`./mvnw`, на Windows — `mvnw.cmd`).

## Запуск всей системы одной командой

```bash
docker compose up --build
```

Первая сборка занимает 10–20 минут: скачиваются образы и зависимости Maven и npm. Следующие запуски — около минуты.

Когда всё поднимется, откройте http://localhost:3000 и войдите одним из тестовых пользователей:

| Логин | Пароль | Роль |
| --- | --- | --- |
| `engineer` | `engineer` | Инженер (Иван Петров) |
| `engineer2` | `engineer2` | Инженер (Сергей Кузнецов) |
| `head` | `head` | Руководитель отдела |
| `admin` | `admin` | Администратор |

Раздел **«Состояние системы»** в меню проверяет всю цепочку: браузер → шлюз → каждый сервис с вашим токеном. Все семь строк должны быть зелёными, в колонке «Роли» — роль пользователя.

Остановить: `docker compose down`. Удалить ещё и данные (БД, файлы): `docker compose down -v`.

### Адреса

| Что | Адрес | Вход |
| --- | --- | --- |
| Приложение | http://localhost:3000 | тестовые пользователи выше |
| API-шлюз | http://localhost:8080 | токен Keycloak |
| Keycloak (админка) | http://localhost:8180/admin | `kcadmin` / `kcadmin` |
| RabbitMQ | http://localhost:15672 | `acts` / `acts` |
| MinIO (консоль) | http://localhost:9001 | `acts` / `acts-secret` |
| Mailpit (письма) | http://localhost:8025 | — |
| PostgreSQL | `localhost:5432` | `postgres` / `postgres`; у каждого сервиса своя БД и пользователь с тем же именем |
| Prometheus, Grafana | http://localhost:9090, http://localhost:3001 | только с профилем `observability`, Grafana: `admin` / `admin` |

Наблюдаемость включается отдельно: `docker compose --profile observability up --build`.

## Структура репозитория

```
pom.xml                    корневой POM: модули и все версии
platform/
  common-web/              безопасность (JWT Keycloak), ошибки RFC 7807, correlation-id, пагинация
  common-events/           конверт и типы событий между сервисами
  common-test/             базовый класс Testcontainers, правила ArchUnit
services/
  api-gateway/             Spring Cloud Gateway: маршруты /api/v1/** по сервисам
  directory-service/       справочники
  parts-service/           каталог запчастей
  act-service/             акты (ядро)
  media-service/           фото и файлы
  document-service/        Excel, профили формы акта
  analytics-service/       часы и нормы
  mail-service/            адресная книга и письма
contracts/openapi/         контракты API (источник правды)
frontend/                  React + TypeScript + Ant Design
infra/                     настройки PostgreSQL, Keycloak, Prometheus, Grafana
docs/                      архитектура, ADR, допущения, бэклог
```

Каждый сервис устроен одинаково (гексагональная архитектура): `domain` → `application` → `adapter` (`in.web`, `in.event`, `out.persistence`, `out.event`, `out.client`) → `config`. Соблюдение слоёв проверяет тест `ArchitectureTest`.

## Сборка и тесты

```bash
./mvnw verify            # всё: компиляция, юнит-тесты, ArchUnit, интеграционные тесты (нужен запущенный Docker)
./mvnw verify -DskipITs  # без интеграционных тестов, если Docker не запущен
./mvnw -pl services/act-service -am verify   # один сервис вместе с общими модулями
```

Фронтенд:

```bash
cd frontend
npm ci
npm run dev              # http://localhost:5173, запросы /api уходят на шлюз localhost:8080
npm run lint
npm test
npm run build
npm run api:generate     # перегенерировать клиент API после изменения contracts/openapi
```

## Как запустить один сервис из IDE

Удобно, когда правите один сервис, а остальное работает в Docker:

1. Поднимите инфраструктуру и остальные сервисы: `docker compose up --build`, затем остановите нужный, например `docker compose stop act-service`.
2. Запустите `ActApplication` из IntelliJ IDEA. Настройки по умолчанию в `application.yml` уже смотрят на `localhost` (БД `acts`, Keycloak на 8180, порт сервиса 8083), шлюз в Docker найдёт его по тому же порту только если вы зададите `ACT_SERVICE_URL=http://host.docker.internal:8083` для `api-gateway`.

## Если что-то не запускается

- **Порт занят** (`port is already allocated`): закройте программу, занимающую порт, или поменяйте левую часть в `ports:` в `docker-compose.yml`.
- **Не скачивается образ** (`manifest unknown`): пришлите мне текст ошибки — версия образа будет заменена.
- **В «Состоянии системы» всё красное с ошибкой 401**: Keycloak ещё запускается; подождите минуту и нажмите «Проверить снова».
- **Изменили `infra/postgres/init-databases.sh` или `infra/keycloak/acts-realm.json`, а изменений нет**: они применяются только на пустых данных — выполните `docker compose down -v` и запустите снова.

## Этапы

План и критерии готовности — в разделе 15 `SPEC.md`. Сделано: этап 0 (уточнения), этап 1 (каркас). Следующий — этап 2: справочники (`directory-service`, `parts-service`) и их экраны.
