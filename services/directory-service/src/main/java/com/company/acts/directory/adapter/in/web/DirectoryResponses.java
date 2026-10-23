package com.company.acts.directory.adapter.in.web;

import com.company.acts.common.core.PageResult;
import com.company.acts.common.web.paging.PageResponse;
import com.company.acts.directory.domain.Department;
import com.company.acts.directory.domain.EquipmentType;
import com.company.acts.directory.domain.Organization;
import com.company.acts.directory.domain.ServiceDepartment;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** Ответы API справочников (схемы из contracts/openapi/directory-api.yaml). */
final class DirectoryResponses {

    static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private DirectoryResponses() {
    }

    record OrganizationResponse(UUID id, String name, String shortName, String inn, String kpp, String ogrn,
                                String legalAddress, String actualAddress, String bankDetails, String phone,
                                String email, String notes, boolean active, Long version) {
        static OrganizationResponse of(Organization o) {
            return new OrganizationResponse(o.id(), o.name(), o.shortName(), o.inn(), o.kpp(), o.ogrn(),
                    o.legalAddress(), o.actualAddress(), o.bankDetails(), o.phone(), o.email(), o.notes(), o.active(),
                    o.version());
        }
    }

    record DepartmentResponse(UUID id, UUID organizationId, UUID parentId, String name, String code, Long version) {
        static DepartmentResponse of(Department d) {
            return new DepartmentResponse(d.id(), d.organizationId(), d.parentId(), d.name(), d.code(), d.version());
        }
    }

    record NamedResponse(UUID id, String name, Long version) {
        static NamedResponse of(EquipmentType t) {
            return new NamedResponse(t.id(), t.name(), t.version());
        }

        static NamedResponse of(ServiceDepartment d) {
            return new NamedResponse(d.id(), d.name(), d.version());
        }
    }

    static <T, R> PageResponse<R> page(PageResult<T> result, Function<T, R> mapper) {
        return new PageResponse<>(result.items().stream().map(mapper).toList(), result.page(), result.size(),
                result.totalElements());
    }

    static ResponseEntity<byte[]> xlsx(String filename, byte[] content) {
        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
                .body(content);
    }
}
