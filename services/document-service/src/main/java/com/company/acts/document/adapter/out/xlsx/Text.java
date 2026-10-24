package com.company.acts.document.adapter.out.xlsx;

import com.company.acts.document.application.model.ActPrintData;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Тексты печатной формы: даты, числа, наработка, реквизиты, заголовки. */
final class Text {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private Text() {
    }

    static String date(LocalDate date) {
        return date == null ? null : DATE.format(date);
    }

    static String number(BigDecimal value) {
        if (value == null) {
            return null;
        }
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.forLanguageTag("ru"));
        symbols.setGroupingSeparator('\u00A0');
        symbols.setDecimalSeparator(',');
        return new DecimalFormat("#,##0.##", symbols).format(value);
    }

    static String meter(ActPrintData d) {
        List<String> parts = new ArrayList<>();
        String type = d.equipment() == null ? "HOURS" : d.equipment().meterType();
        if (d.meterHours() != null && !"KM".equals(type)) {
            parts.add(number(d.meterHours()) + " м/ч");
        }
        if (d.meterKm() != null && !"HOURS".equals(type)) {
            parts.add(number(d.meterKm()) + " км");
        }
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    static String period(ActPrintData d) {
        if (d.workStart() == null && d.workEnd() == null) {
            return null;
        }
        String range = d.workStart() != null && d.workEnd() != null && !d.workStart().equals(d.workEnd())
                ? date(d.workStart()) + " — " + date(d.workEnd())
                : date(d.workStart() != null ? d.workStart() : d.workEnd());
        return d.calendarDays() > 0 ? range + " (" + d.calendarDays() + " дн.)" : range;
    }

    /** Реквизиты одной строкой; null, если реквизиты скрыты или пусты (раздел 16.1 SPEC.md). */
    static String requisites(boolean hidden, String inn, String kpp, String address, String phone) {
        if (hidden) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        if (inn != null) {
            parts.add("ИНН " + inn);
        }
        if (kpp != null) {
            parts.add("КПП " + kpp);
        }
        if (address != null) {
            parts.add(address);
        }
        if (phone != null) {
            parts.add("тел. " + phone);
        }
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    static String title(ActPrintData d) {
        return d.number() == null ? "ЧЕРНОВИК АКТА ВЫПОЛНЕННЫХ РАБОТ" : "АКТ ВЫПОЛНЕННЫХ РАБОТ № " + d.number();
    }

    static String equipmentLine(ActPrintData d) {
        if (d.equipment() == null) {
            return null;
        }
        ActPrintData.Equipment e = d.equipment();
        List<String> parts = new ArrayList<>();
        parts.add((e.type() != null ? e.type() + " " : "") + e.brand() + " " + e.model());
        if (e.serialNumber() != null) {
            parts.add("S/N " + e.serialNumber());
        }
        if (e.garageNumber() != null) {
            parts.add("гар. № " + e.garageNumber());
        }
        if (e.plateNumber() != null) {
            parts.add("госномер " + e.plateNumber());
        }
        String meter = meter(d);
        if (meter != null) {
            parts.add("наработка " + meter);
        }
        return String.join(", ", parts);
    }
}
