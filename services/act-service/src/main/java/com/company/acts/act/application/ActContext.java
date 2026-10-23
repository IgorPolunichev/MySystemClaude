package com.company.acts.act.application;

import com.company.acts.act.domain.ActPolicy;
import com.company.acts.act.domain.CurrentUser;
import com.company.acts.act.domain.Participant;
import com.company.acts.act.domain.WorkAct;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;

/** Загруженный акт с участниками и правами текущего пользователя. */
record ActContext(WorkAct act, List<Participant> participants, CurrentUser user, ActPolicy policy) {

    static ActContext of(WorkAct act, List<Participant> participants, CurrentUser user) {
        return new ActContext(act, participants, user, new ActPolicy(act, participants, user));
    }

    void require(boolean allowed, String message) {
        if (!allowed) {
            throw new AccessDeniedException(message);
        }
    }
}
