package com.company.acts.document.application.port.out;

import com.company.acts.document.application.model.ActPrintData;

/** Печатная форма акта в конкретном формате (сейчас — Excel; позже PDF и другие). */
public interface DocumentRenderer {

    String format();

    byte[] render(ActPrintData data);
}
