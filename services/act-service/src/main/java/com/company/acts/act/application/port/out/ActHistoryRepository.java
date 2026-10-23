package com.company.acts.act.application.port.out;

import com.company.acts.act.application.view.HistoryEntry;
import java.util.List;
import java.util.UUID;

public interface ActHistoryRepository {

    void append(UUID actId, String action, String details, String user);

    List<HistoryEntry> findByAct(UUID actId);
}
