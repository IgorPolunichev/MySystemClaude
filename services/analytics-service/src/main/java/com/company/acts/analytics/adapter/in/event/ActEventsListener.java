package com.company.acts.analytics.adapter.in.event;

import com.company.acts.analytics.application.EventService;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

/**
 * Приём событий act-service из RabbitMQ: очередь {@code analytics.events}, привязанная ко всем событиям
 * обменника {@code acts.events}. Ошибочное сообщение (не JSON, неизвестный формат) записывается в журнал и
 * пропускается, чтобы не блокировать очередь; расхождения устраняет полная пересборка.
 */
@Component
class ActEventsListener {

    static final String EXCHANGE = "acts.events";
    static final String QUEUE = "analytics.events";
    private static final Logger log = LoggerFactory.getLogger(ActEventsListener.class);

    private final EventService events;

    ActEventsListener(EventService events) {
        this.events = events;
    }

    @RabbitListener(queues = QUEUE)
    void onMessage(Message message) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        try {
            events.apply(JsonReader.object(body));
        } catch (IllegalArgumentException e) {
            log.error("Не удалось разобрать событие, пропущено: {}", e.getMessage());
        }
    }

    /** Обменник, очередь и привязка объявляются в RabbitMQ при подключении. */
    @Configuration(proxyBeanMethods = false)
    static class Topology {
        @Bean
        Declarables analyticsTopology() {
            TopicExchange exchange = new TopicExchange(EXCHANGE, true, false);
            Queue queue = new Queue(QUEUE, true);
            Binding binding = BindingBuilder.bind(queue).to(exchange).with("#");
            return new Declarables(exchange, queue, binding);
        }
    }
}
