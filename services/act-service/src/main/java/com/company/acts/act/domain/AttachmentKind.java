package com.company.acts.act.domain;

/** Тип документа, прикреплённого к акту. */
public enum AttachmentKind {
    /** Скан подписанного акта. */
    SIGNED_ACT,
    /** Другой документ: письмо, заявка, протокол и т. п. */
    OTHER
}
