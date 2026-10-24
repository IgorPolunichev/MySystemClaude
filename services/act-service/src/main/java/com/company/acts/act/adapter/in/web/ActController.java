package com.company.acts.act.adapter.in.web;

import com.company.acts.act.application.ActContentService;
import com.company.acts.act.application.ActPhotoService;
import com.company.acts.act.application.ActService;
import com.company.acts.act.application.command.ActCreateCommand;
import com.company.acts.act.application.command.ActHeaderCommand;
import com.company.acts.act.application.command.CommentCommand;
import com.company.acts.act.application.command.PartLineCommand;
import com.company.acts.act.application.command.TimeEntryCommand;
import com.company.acts.act.application.view.ActDetail;
import com.company.acts.act.application.view.ActSearchQuery;
import com.company.acts.act.application.view.ActSummary;
import com.company.acts.act.application.view.HistoryEntry;
import com.company.acts.act.domain.ActStatus;
import com.company.acts.common.core.PageQuery;
import com.company.acts.common.web.paging.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/acts")
class ActController {

    record AddEngineerRequest(@NotNull(message = "Выберите инженера") UUID engineerId) {
    }

    record AddPhotoRequest(@NotNull(message = "Не указан файл фото") UUID mediaId,
                           @jakarta.validation.constraints.Size(max = 200) String caption) {
    }

    record CaptionRequest(@jakarta.validation.constraints.Size(max = 200) String caption) {
    }

    record PhotoOrderRequest(@NotNull List<UUID> photoIds) {
    }

    record AddAttachmentRequest(@NotNull(message = "Не указан файл") UUID mediaId,
                                com.company.acts.act.domain.AttachmentKind kind,
                                @jakarta.validation.constraints.Size(max = 300) String comment) {
    }

    private final ActService acts;
    private final ActContentService content;
    private final ActPhotoService photos;
    private final com.company.acts.act.application.ActAttachmentService attachments;

    ActController(ActService acts, ActContentService content, ActPhotoService photos,
                  com.company.acts.act.application.ActAttachmentService attachments) {
        this.acts = acts;
        this.content = content;
        this.photos = photos;
        this.attachments = attachments;
    }

    @GetMapping
    PageResponse<ActSummary> list(@RequestParam(required = false) String q,
                                  @RequestParam(required = false) List<ActStatus> status,
                                  @RequestParam(required = false) UUID organizationId,
                                  @RequestParam(required = false) UUID equipmentId,
                                  @RequestParam(required = false) UUID engineerId,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                  @RequestParam(required = false) Boolean mine,
                                  @RequestParam(required = false) Integer page,
                                  @RequestParam(required = false) Integer size,
                                  @RequestParam(required = false) String sort) {
        Set<ActStatus> statuses = status == null || status.isEmpty() ? null : EnumSet.copyOf(status);
        ActSearchQuery query = new ActSearchQuery(q, statuses, organizationId, equipmentId, engineerId, null, from, to);
        PageQuery pageQuery = PageQuery.of(page, size, sort);
        var result = Boolean.TRUE.equals(mine) ? acts.searchMine(query, pageQuery) : acts.search(query, pageQuery);
        return new PageResponse<>(result.items(), result.page(), result.size(), result.totalElements());
    }

    @GetMapping("/short-descriptions")
    List<String> shortDescriptions(@RequestParam(required = false) String q) {
        return acts.shortDescriptions(q);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ActDetail create(@Valid @RequestBody ActCreateCommand command) {
        return acts.create(command);
    }

    @GetMapping("/{id}")
    ActDetail get(@PathVariable UUID id) {
        return acts.get(id);
    }

    @PutMapping("/{id}")
    ActDetail update(@PathVariable UUID id, @Valid @RequestBody ActHeaderCommand command) {
        return acts.updateHeader(id, command);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id) {
        acts.delete(id);
    }

    @PostMapping("/{id}/open")
    ActDetail open(@PathVariable UUID id) {
        return acts.open(id);
    }

    @PostMapping("/{id}/close")
    ActDetail close(@PathVariable UUID id) {
        return acts.close(id);
    }

    @PostMapping("/{id}/reopen")
    ActDetail reopen(@PathVariable UUID id, @Valid @RequestBody CommentCommand command) {
        return acts.reopen(id, command.comment());
    }

    @PostMapping("/{id}/cancel")
    ActDetail cancel(@PathVariable UUID id, @Valid @RequestBody CommentCommand command) {
        return acts.cancel(id, command.comment());
    }

    @PostMapping("/{id}/copy")
    @ResponseStatus(HttpStatus.CREATED)
    ActDetail copy(@PathVariable UUID id) {
        return acts.copy(id);
    }

    @GetMapping("/{id}/history")
    List<HistoryEntry> history(@PathVariable UUID id) {
        return acts.history(id);
    }

    @PostMapping("/{id}/engineers")
    ActDetail addEngineer(@PathVariable UUID id, @Valid @RequestBody AddEngineerRequest request) {
        return content.addEngineer(id, request.engineerId());
    }

    @DeleteMapping("/{id}/engineers/{engineerId}")
    ActDetail removeEngineer(@PathVariable UUID id, @PathVariable UUID engineerId) {
        return content.removeEngineer(id, engineerId);
    }

    @PutMapping("/{id}/time/{engineerId}/{date}")
    ActContentService.TimeEntryResult putTime(@PathVariable UUID id, @PathVariable UUID engineerId,
                                              @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                              @Valid @RequestBody TimeEntryCommand command) {
        return content.putTime(id, engineerId, date, command);
    }

    @DeleteMapping("/{id}/time/{engineerId}/{date}")
    ActDetail deleteTime(@PathVariable UUID id, @PathVariable UUID engineerId,
                         @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return content.deleteTime(id, engineerId, date);
    }

    @PostMapping("/{id}/parts")
    ActDetail addPart(@PathVariable UUID id, @Valid @RequestBody PartLineCommand command) {
        return content.addPart(id, command);
    }

    @PutMapping("/{id}/parts/{lineId}")
    ActDetail updatePart(@PathVariable UUID id, @PathVariable UUID lineId, @Valid @RequestBody PartLineCommand command) {
        return content.updatePart(id, lineId, command);
    }

    @DeleteMapping("/{id}/parts/{lineId}")
    ActDetail deletePart(@PathVariable UUID id, @PathVariable UUID lineId) {
        return content.deletePart(id, lineId);
    }

    @PostMapping("/{id}/photos")
    ActDetail addPhoto(@PathVariable UUID id, @Valid @RequestBody AddPhotoRequest request) {
        return photos.add(id, request.mediaId(), request.caption());
    }

    @PutMapping("/{id}/photos/order")
    ActDetail reorderPhotos(@PathVariable UUID id, @Valid @RequestBody PhotoOrderRequest request) {
        return photos.reorder(id, request.photoIds());
    }

    @PutMapping("/{id}/photos/{photoId}")
    ActDetail captionPhoto(@PathVariable UUID id, @PathVariable UUID photoId, @Valid @RequestBody CaptionRequest request) {
        return photos.caption(id, photoId, request.caption());
    }

    @DeleteMapping("/{id}/photos/{photoId}")
    ActDetail deletePhoto(@PathVariable UUID id, @PathVariable UUID photoId) {
        return photos.delete(id, photoId);
    }

    @PostMapping("/{id}/attachments")
    ActDetail addAttachment(@PathVariable UUID id, @Valid @RequestBody AddAttachmentRequest request) {
        return attachments.add(id, request.mediaId(), request.kind(), request.comment());
    }

    @DeleteMapping("/{id}/attachments/{attachmentId}")
    ActDetail deleteAttachment(@PathVariable UUID id, @PathVariable UUID attachmentId) {
        return attachments.delete(id, attachmentId);
    }
}
