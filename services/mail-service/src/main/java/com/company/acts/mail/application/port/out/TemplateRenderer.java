package com.company.acts.mail.application.port.out;

import java.util.Map;

/** Подстановка данных в шаблон (FreeMarker). Тема — обычный текст, тело — HTML с экранированием значений. */
public interface TemplateRenderer {

    String renderText(String template, Map<String, Object> model);

    String renderHtml(String template, Map<String, Object> model);
}
