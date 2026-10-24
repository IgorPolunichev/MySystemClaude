package com.company.acts.act.application;

import com.company.acts.act.application.port.out.ActHistoryRepository;
import com.company.acts.act.application.port.out.PhotoRepository;
import com.company.acts.act.application.view.ActDetail;
import com.company.acts.act.domain.ActPhoto;
import com.company.acts.act.domain.ActStatus;
import com.company.acts.common.core.BusinessRuleException;
import com.company.acts.common.core.NotFoundException;
import com.company.acts.common.core.Uuid7;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Фото технического отчёта: добавить (файл уже загружен в media-service), подписать, упорядочить, удалить. */
@Service
public class ActPhotoService {

    private final PhotoRepository photos;
    private final ActHistoryRepository history;
    private final ActLoader loader;
    private final ActDetailAssembler assembler;
    private final com.company.acts.act.application.port.out.ActRepository acts;

    public ActPhotoService(PhotoRepository photos, ActHistoryRepository history, ActLoader loader,
                           ActDetailAssembler assembler, com.company.acts.act.application.port.out.ActRepository acts) {
        this.acts = acts;
        this.photos = photos;
        this.history = history;
        this.loader = loader;
        this.assembler = assembler;
    }

    @Transactional
    public ActDetail add(UUID actId, UUID mediaId, String caption) {
        ActContext ctx = editable(actId);
        if (photos.findByAct(actId).stream().anyMatch(p -> p.mediaId().equals(mediaId))) {
            return assembler.assemble(ctx);
        }
        ActPhoto photo = photos.save(new ActPhoto(Uuid7.next(), actId, mediaId, caption, photos.nextPosition(actId)));
        acts.refreshAggregates(actId);
        history.append(actId, "PHOTO_ADDED", photo.caption(), ctx.user().username());
        return assembler.assemble(loader.load(actId));
    }

    @Transactional
    public ActDetail caption(UUID actId, UUID photoId, String caption) {
        ActContext ctx = editable(actId);
        ActPhoto current = owned(actId, photoId);
        photos.save(new ActPhoto(photoId, actId, current.mediaId(), caption, current.position()));
        acts.refreshAggregates(actId);
        return assembler.assemble(ctx);
    }

    /** Новый порядок: полный список id фото акта в нужной последовательности. */
    @Transactional
    public ActDetail reorder(UUID actId, List<UUID> photoIds) {
        ActContext ctx = editable(actId);
        Map<UUID, ActPhoto> current = photos.findByAct(actId).stream()
                .collect(Collectors.toMap(ActPhoto::id, Function.identity()));
        if (photoIds == null || photoIds.size() != current.size() || !current.keySet().equals(new HashSet<>(photoIds))) {
            throw new BusinessRuleException("photoIds", "Передайте все фото акта ровно по одному разу");
        }
        int position = 1;
        for (UUID id : photoIds) {
            ActPhoto p = current.get(id);
            photos.save(new ActPhoto(p.id(), actId, p.mediaId(), p.caption(), position++));
        }
        return assembler.assemble(ctx);
    }

    @Transactional
    public ActDetail delete(UUID actId, UUID photoId) {
        ActContext ctx = editable(actId);
        ActPhoto photo = owned(actId, photoId);
        photos.delete(photoId);
        acts.refreshAggregates(actId);
        history.append(actId, "PHOTO_REMOVED", photo.caption(), ctx.user().username());
        return assembler.assemble(loader.load(actId));
    }

    private ActPhoto owned(UUID actId, UUID photoId) {
        return photos.findById(photoId).filter(p -> p.actId().equals(actId))
                .orElseThrow(() -> NotFoundException.of("Фото", photoId));
    }

    private ActContext editable(UUID actId) {
        ActContext ctx = loader.load(actId);
        ctx.require(ctx.policy().canEdit(), ctx.act().status() == ActStatus.CLOSED
                ? "Акт закрыт: изменить его может только администратор"
                : "Изменять акт могут его инженеры, руководитель и администратор");
        return ctx;
    }
}
