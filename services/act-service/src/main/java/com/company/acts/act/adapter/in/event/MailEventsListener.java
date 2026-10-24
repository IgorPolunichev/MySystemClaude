package com.company.acts.act.adapter.in.event;

import com.company.acts.act.application.EmailSentHandler;
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

/** Очередь {@code acts.mail-events}: события EmailSent от mail-service → отметка «Отправлен» у актов. */
@Component
class MailEventsListener {

    static final String QUEUE = "acts.mail-events";
    private static final Logger log = LoggerFactory.getLogger(MailEventsListener.class);

    private final EmailSentHandler handler;

    MailEventsListener(EmailSentHandler handler) {
        this.handler = handler;
    }

    @RabbitListener(queues = QUEUE)
    void onMessage(Message message) {
        try {
            handler.handle(JsonReader.object(new String(message.getBody(), StandardCharsets.UTF_8)));
        } catch (IllegalArgumentException e) {
            log.error("Не удалось разобрать событие EmailSent, пропущено: {}", e.getMessage());
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class Topology {
        @Bean
        Declarables mailEventsTopology() {
            TopicExchange exchange = new TopicExchange("acts.events", true, false);
            Queue queue = new Queue(QUEUE, true);
            Binding binding = BindingBuilder.bind(queue).to(exchange).with("EmailSent");
            return new Declarables(exchange, queue, binding);
        }
    }
}
