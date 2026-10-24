package com.company.acts.mail.application;

import com.company.acts.mail.application.port.out.ActSource;
import com.company.acts.mail.application.port.out.CurrentUserProvider;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Данные для шаблона (раздел 10 SPEC.md): {@code acts} (номер, организация, период, техника, краткое описание,
 * часы), {@code sender} (ФИО, должность, телефон, почта), {@code today}, {@code totalHours}. Все значения —
 * строки, пустые — "" (чтобы шаблон не падал на отсутствующем значении).
 */
final class MailModel {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private MailModel() {
    }

    static Map<String, Object> of(List<ActSource.ActInfo> acts, CurrentUserProvider.CurrentUser sender) {
        List<Map<String, Object>> list = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (ActSource.ActInfo a : acts) {
            Map<String, Object> m = new HashMap<>();
            m.put("number", s(a.number() != null ? a.number() : "черновик"));
            m.put("organization", s(a.organizationName()));
            m.put("period", s(a.period()));
            m.put("equipment", s(a.equipment()));
            m.put("shortDescription", s(a.shortDescription()));
            m.put("totalHours", hours(a.totalHours()));
            list.add(m);
            if (a.totalHours() != null) {
                total = total.add(a.totalHours());
            }
        }
        Map<String, Object> s = new HashMap<>();
        s.put("fullName", s(sender.fullName() != null ? sender.fullName() : sender.username()));
        s.put("position", "");
        s.put("phone", "");
        s.put("email", s(sender.email()));
        Map<String, Object> model = new HashMap<>();
        model.put("acts", list);
        model.put("sender", s);
        model.put("today", DATE.format(LocalDate.now(ZoneId.of("Europe/Moscow"))));
        model.put("totalHours", hours(total));
        return model;
    }

    static Map<String, Object> sample(CurrentUserProvider.CurrentUser sender) {
        return of(List.of(new ActSource.ActInfo(UUID.randomUUID(), "АВР-2026-000001", "CLOSED", null,
                "АО «Заказчик»", "05.10.2026 — 07.10.2026", "CAT 777G, гар. № 45", "ТО-2000", new BigDecimal("21.5"),
                null)), sender);
    }

    private static String s(String value) {
        return value == null ? "" : value;
    }

    private static String hours(BigDecimal value) {
        if (value == null) {
            return "";
        }
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.forLanguageTag("ru"));
        symbols.setDecimalSeparator(',');
        symbols.setGroupingSeparator('\u00A0');
        return new DecimalFormat("#,##0.##", symbols).format(value);
    }
}
