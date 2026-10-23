package com.company.acts.parts.adapter.out.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UnitsJpaRepository extends JpaRepository<UnitJpaEntity, String> {

    List<UnitJpaEntity> findAllByOrderBySortOrderAsc();
}
