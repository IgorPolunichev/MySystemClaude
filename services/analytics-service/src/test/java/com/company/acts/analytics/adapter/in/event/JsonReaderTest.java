package com.company.acts.analytics.adapter.in.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonReaderTest {

    @Test
    void parsesEventEnvelope() {
        Map<String, Object> m = JsonReader.object("""
                {"eventId":"01a1","eventType":"TimeEntryChanged","actorId":null,"payload":{"workMinutes":450,
                 "ok":true,"text":"т\\"x\\u0410","list":[1,"a"],"empty":{}}}""");
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) m.get("payload");
        assertThat(m.get("actorId")).isNull();
        assertThat(payload.get("workMinutes")).isEqualTo("450");
        assertThat(payload.get("ok")).isEqualTo(true);
        assertThat(payload.get("text")).isEqualTo("т\"xА");
        assertThat(payload.get("list")).isEqualTo(List.of("1", "a"));
        assertThatThrownBy(() -> JsonReader.parse("{\"a\":}")).isInstanceOf(IllegalArgumentException.class);
    }
}
