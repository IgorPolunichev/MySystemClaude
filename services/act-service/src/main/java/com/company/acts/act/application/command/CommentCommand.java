package com.company.acts.act.application.command;

import jakarta.validation.constraints.Size;

/** Комментарий к действию (возврат в работу, аннулирование). */
public record CommentCommand(@Size(max = 1000) String comment) {
}
