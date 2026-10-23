package com.company.acts.act.application;

import com.company.acts.act.application.port.out.ActRepository;
import com.company.acts.act.application.port.out.CurrentUserProvider;
import com.company.acts.act.application.port.out.ParticipantRepository;
import com.company.acts.common.core.NotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Загрузка акта с участниками и правами — общая для всех сценариев. */
@Component
class ActLoader {

    private final ActRepository acts;
    private final ParticipantRepository participants;
    private final CurrentUserProvider users;

    ActLoader(ActRepository acts, ParticipantRepository participants, CurrentUserProvider users) {
        this.acts = acts;
        this.participants = participants;
        this.users = users;
    }

    ActContext load(UUID id) {
        var act = acts.findById(id).orElseThrow(() -> NotFoundException.of("Акт", id));
        return ActContext.of(act, participants.findByAct(id), users.current());
    }
}
