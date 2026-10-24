package com.company.acts.mail.application.port.out;

import java.util.Map;
import java.util.UUID;

public interface EventPublisher {

    void publish(String eventType, UUID aggregateId, Map<String, Object> payload);
}
