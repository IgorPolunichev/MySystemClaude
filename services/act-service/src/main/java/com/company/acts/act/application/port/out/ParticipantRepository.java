package com.company.acts.act.application.port.out;

import com.company.acts.act.domain.Participant;
import java.util.List;
import java.util.UUID;

public interface ParticipantRepository {

    List<Participant> findByAct(UUID actId);

    void save(UUID actId, Participant participant);

    void delete(UUID actId, UUID engineerId);
}
