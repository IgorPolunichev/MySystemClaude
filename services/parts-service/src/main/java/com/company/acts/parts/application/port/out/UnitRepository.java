package com.company.acts.parts.application.port.out;

import com.company.acts.parts.domain.Unit;
import java.util.List;
import java.util.Optional;

public interface UnitRepository {

    List<Unit> findAll();

    Optional<Unit> findByCode(String code);
}
