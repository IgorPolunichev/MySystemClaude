# Архитектура

Подробное описание — в разделе 4 `SPEC.md`, решения — в `docs/adr/`. Здесь — схемы текущего состояния.

## Контекст (C4, уровень 1)

```mermaid
flowchart LR
    engineer([Сервисный инженер]) --> app[Сервисные акты]
    head([Руководитель отдела]) --> app
    admin([Администратор]) --> app
    app -- письма с актами --> mail[(Почтовый сервер)]
    mail --> accounting[Программа закрытия актов]
```

## Контейнеры (C4, уровень 2)

```mermaid
flowchart TB
    browser[Браузер / PWA<br/>React + Ant Design] -->|HTTPS| nginx[Nginx<br/>статика фронтенда]
    browser -->|вход, OIDC + PKCE| kc[Keycloak]
    nginx -->|/api/**| gw[api-gateway<br/>Spring Cloud Gateway]

    gw --> dir[directory-service]
    gw --> parts[parts-service]
    gw --> act[act-service]
    gw --> media[media-service]
    gw --> doc[document-service]
    gw --> an[analytics-service]
    gw --> ml[mail-service]

    dir & parts & act & media & doc & an & ml --> pg[(PostgreSQL<br/>своя БД у каждого сервиса)]
    act & dir & doc & ml -. события, outbox .-> mq[[RabbitMQ]]
    mq -.-> an & parts & act & ml
    media --> s3[(RustFS, S3 API<br/>фото и файлы)]
    ml --> smtp[SMTP / Mailpit]
    gw & dir & act -. ключи JWT .-> kc
```

## Устройство сервиса

```mermaid
flowchart LR
    web[adapter.in.web<br/>REST] --> portIn[application.port.in]
    evIn[adapter.in.event] --> portIn
    portIn --> app[application<br/>сценарии]
    app --> domain[domain<br/>чистая модель]
    app --> portOut[application.port.out]
    persistence[adapter.out.persistence] -.реализует.-> portOut
    evOut[adapter.out.event<br/>outbox] -.реализует.-> portOut
    client[adapter.out.client] -.реализует.-> portOut
```

Стрелки — направление зависимостей. Домен ни от чего не зависит; адаптеры зависят от портов, а не наоборот. Проверяется ArchUnit.
