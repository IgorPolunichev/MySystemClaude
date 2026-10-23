package com.company.acts.common.web.error;

import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Единый формат ошибок RFC 7807 ({@link ProblemDetail}). Стандартные ошибки Spring MVC (валидация,
 * неверный запрос и т. п.) обрабатывает {@link ResponseEntityExceptionHandler}; здесь — непредвиденные.
 * Поле {@code detail} — на русском, для показа пользователю.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
                "Недостаточно прав для этого действия.");
        problem.setType(URI.create("urn:acts:problem:forbidden"));
        problem.setTitle("Доступ запрещён");
        return problem;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex) {
        log.error("Непредвиденная ошибка", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "Внутренняя ошибка сервера. Повторите действие позже или сообщите администратору.");
        problem.setType(URI.create("urn:acts:problem:internal"));
        problem.setTitle("Внутренняя ошибка");
        return problem;
    }
}
