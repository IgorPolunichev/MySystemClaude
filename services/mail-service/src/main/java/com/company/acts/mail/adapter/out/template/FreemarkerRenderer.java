package com.company.acts.mail.adapter.out.template;

import com.company.acts.mail.application.port.out.TemplateRenderer;
import freemarker.core.HTMLOutputFormat;
import freemarker.core.PlainTextOutputFormat;
import freemarker.core.TemplateClassResolver;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * FreeMarker без доступа к Java-классам из шаблона (шаблоны правит администратор, но рисковать незачем).
 * В HTML-теле значения экранируются автоматически.
 */
@Component
class FreemarkerRenderer implements TemplateRenderer {

    private final Configuration text = configuration(false);
    private final Configuration html = configuration(true);

    @Override
    public String renderText(String template, Map<String, Object> model) {
        return render(text, template, model);
    }

    @Override
    public String renderHtml(String template, Map<String, Object> model) {
        return render(html, template, model);
    }

    private static String render(Configuration cfg, String source, Map<String, Object> model) {
        try {
            Template t = new Template("mail", new StringReader(source), cfg);
            StringWriter out = new StringWriter();
            t.process(model, out);
            return out.toString();
        } catch (TemplateException | IOException e) {
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            throw new IllegalArgumentException(message.lines().findFirst().orElse(message));
        }
    }

    private static Configuration configuration(boolean htmlOutput) {
        Configuration cfg = new Configuration(Configuration.VERSION_2_3_32);
        cfg.setDefaultEncoding("UTF-8");
        cfg.setLocale(Locale.forLanguageTag("ru"));
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        cfg.setLogTemplateExceptions(false);
        cfg.setWrapUncheckedExceptions(true);
        cfg.setNewBuiltinClassResolver(TemplateClassResolver.ALLOWS_NOTHING_RESOLVER);
        cfg.setAPIBuiltinEnabled(false);
        cfg.setOutputFormat(htmlOutput ? HTMLOutputFormat.INSTANCE : PlainTextOutputFormat.INSTANCE);
        return cfg;
    }
}
