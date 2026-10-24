package com.company.acts.document.adapter.out.xlsx;

import com.company.acts.document.application.model.ActPrintData;
import org.apache.poi.ss.usermodel.Row;

/** Блок подписей: слева представитель заказчика, справа ответственный сервисный инженер. */
final class Signatures {

    private Signatures() {
    }

    /** Колонки [leftFrom..leftTo] — заказчик, [rightFrom..rightTo] — инженер. */
    static void write(SheetWriter w, ActPrintData d, int leftFrom, int leftTo, int rightFrom, int rightTo) {
        ActPrintData.Engineer lead = d.engineers().stream().filter(ActPrintData.Engineer::lead).findFirst()
                .orElse(d.engineers().isEmpty() ? null : d.engineers().getFirst());
        w.row(10);
        Row head = w.row(18);
        w.put(head, leftFrom, leftTo, "Представитель заказчика", w.styles.section);
        w.put(head, rightFrom, rightTo, "Сервисный инженер", w.styles.section);
        Row who = w.row(30);
        w.put(who, leftFrom, leftTo, joined(d.customerRepPosition(), d.customerRepName()), w.styles.signature);
        w.put(who, rightFrom, rightTo, lead == null ? null : joined(lead.position(), lead.fullName()), w.styles.signature);
        Row line = w.row(26);
        w.put(line, leftFrom, leftTo, null, w.styles.signatureLine);
        w.put(line, rightFrom, rightTo, null, w.styles.signatureLine);
        Row hint = w.row(12);
        w.put(hint, leftFrom, leftTo, "подпись / расшифровка", w.styles.small);
        w.put(hint, rightFrom, rightTo, "подпись / расшифровка", w.styles.small);
        Row date = w.row(20);
        w.put(date, leftFrom, leftTo, "Дата: «___» ____________ 20___ г.", w.styles.signature);
        w.put(date, rightFrom, rightTo, "Дата: «___» ____________ 20___ г.", w.styles.signature);
    }

    private static String joined(String position, String name) {
        if (position == null && name == null) {
            return null;
        }
        return position == null ? name : name == null ? position : position + ", " + name;
    }
}
