package com.company.acts.act.application.command;

import java.util.UUID;

/** Новый черновик; id может выдать клиент (UUID v7) — тогда повтор запроса не создаёт дубль. */
public record ActCreateCommand(UUID id, UUID equipmentId) {
}
