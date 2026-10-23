package com.company.acts.parts.application.port.out;

import com.company.acts.common.core.PageQuery;
import com.company.acts.common.core.PageResult;
import com.company.acts.parts.domain.Part;
import java.util.Optional;
import java.util.UUID;

public interface PartRepository {

    Optional<Part> findById(UUID id);

    Optional<Part> findByCatalogNumber(String catalogNumber, String manufacturer);

    Optional<Part> findByNameIgnoreCase(String name);

    PageResult<Part> search(String text, PageQuery page);

    Part save(Part part);

    void delete(UUID id);
}
