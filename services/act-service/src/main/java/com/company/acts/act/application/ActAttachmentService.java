package com.company.acts.act.application;

import com.company.acts.act.application.port.out.ActHistoryRepository;
import com.company.acts.act.application.port.out.ActRepository;
import com.company.acts.act.application.port.out.AttachmentRepository;
import com.company.acts.act.application.port.out.MediaClient;
import com.company.acts.act.application.view.ActDetail;
import com.company.acts.act.domain.ActAttachment;
import com.company.acts.act.domain.AttachmentKind;
import com.company.acts.common.core.BusinessRuleException;
import com.company.acts.common.core.NotFoundException;
import com.company.acts.common.core.Uuid7;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Документы акта: скан подписанного акта и другие файлы. Файл сначала загружается в media-service
 * (назначение ACT_ATTACHMENT, владелец — этот акт), затем привязывается здесь; имя, тип и размер берутся из
 * media-service, а не со слов клиента.
 */
@Service
public class ActAttachmentService {

    public static final String OWNER_TYPE = "ACT_ATTACHMENT";

    private final AttachmentRepository attachments;
    private final MediaClient media;
    private final ActRepository acts;
    private final ActHistoryRepository history;
    private final ActLoader loader;
    private final ActDetailAssembler assembler;

    public ActAttachmentService(AttachmentRepository attachments, MediaClient media, ActRepository acts,
                                ActHistoryRepository history, ActLoader loader, ActDetailAssembler assembler) {
        this.attachments = attachments;
        this.media = media;
        this.acts = acts;
        this.history = history;
        this.loader = loader;
        this.assembler = assembler;
    }

    @Transactional
    public ActDetail add(UUID actId, UUID mediaId, AttachmentKind kind, String comment) {
        ActContext ctx = loader.load(actId);
        ctx.require(ctx.policy().canAttach(), "Прикреплять документы могут инженеры акта, руководитель и администратор");
        if (attachments.findByAct(actId).stream().anyMatch(a -> a.mediaId().equals(mediaId))) {
            return assembler.assemble(ctx);
        }
        MediaClient.MediaInfo file = media.media(mediaId);
        if (!OWNER_TYPE.equals(file.ownerType()) || !actId.equals(file.ownerId())) {
            throw new BusinessRuleException("mediaId", "Файл загружен не для этого акта");
        }
        ActAttachment attachment = attachments.save(new ActAttachment(Uuid7.next(), actId, mediaId, kind,
                file.originalFilename(), file.contentType(), file.sizeBytes(), comment, ctx.user().username(),
                Instant.now()));
        acts.refreshAggregates(actId);
        history.append(actId, "ATTACHMENT_ADDED", attachment.fileName(), ctx.user().username());
        return assembler.assemble(loader.load(actId));
    }

    @Transactional
    public ActDetail delete(UUID actId, UUID attachmentId) {
        ActContext ctx = loader.load(actId);
        ActAttachment attachment = attachments.findById(attachmentId).filter(a -> a.actId().equals(actId))
                .orElseThrow(() -> NotFoundException.of("Документ", attachmentId));
        ctx.require(ctx.policy().canDeleteAttachment(attachment.uploadedBy()),
                "Удалить документ могут загрузивший его, руководитель и администратор");
        attachments.delete(attachmentId);
        acts.refreshAggregates(actId);
        history.append(actId, "ATTACHMENT_REMOVED", attachment.fileName(), ctx.user().username());
        return assembler.assemble(loader.load(actId));
    }
}
