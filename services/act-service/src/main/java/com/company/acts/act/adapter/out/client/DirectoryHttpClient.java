package com.company.acts.act.adapter.out.client;

import com.company.acts.act.application.port.out.DirectoryClient;
import com.company.acts.common.core.BusinessRuleException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** directory-service по HTTP. Ответы читаются в узкие записи — лишние поля игнорируются. */
@Component
class DirectoryHttpClient implements DirectoryClient {

    private static final Logger log = LoggerFactory.getLogger(DirectoryHttpClient.class);

    @JsonIgnoreProperties(ignoreUnknown = true)
    record EquipmentDto(UUID id, UUID organizationId, UUID departmentId, String departmentName, UUID siteId,
                        String equipmentTypeName, String brand, String model, String serialNumber, String garageNumber,
                        String plateNumber, String meterType, BigDecimal currentHours, BigDecimal currentKm) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OrganizationDto(UUID id, String name, String shortName, String inn, String kpp, String legalAddress) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record EngineerDto(UUID id, String keycloakUserId, String fullName, String position, boolean active) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ExecutorDto(UUID id, String name, String shortName, String inn, String kpp, String address, String phone,
                       boolean active) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SiteDto(UUID id, UUID organizationId, String name, String code, String address, boolean active) {
    }

    private final RestClient client;

    DirectoryHttpClient(@Value("${app.clients.directory-url:http://localhost:8081}") String baseUrl) {
        this.client = BearerRelay.client(baseUrl);
    }

    @Override
    public EquipmentInfo equipment(UUID id) {
        EquipmentDto e = get("/api/v1/equipment/{id}", EquipmentDto.class, id, "equipmentId", "Техника не найдена");
        return new EquipmentInfo(e.id(), e.organizationId(), e.departmentId(), e.departmentName(), e.siteId(),
                e.equipmentTypeName(), e.brand(), e.model(), e.serialNumber(), e.garageNumber(), e.plateNumber(),
                e.meterType(), e.currentHours(), e.currentKm());
    }

    @Override
    public OrganizationInfo organization(UUID id) {
        OrganizationDto o = get("/api/v1/organizations/{id}", OrganizationDto.class, id, "organizationId",
                "Организация не найдена");
        return new OrganizationInfo(o.id(), o.name(), o.shortName(), o.inn(), o.kpp(), o.legalAddress());
    }

    @Override
    public EngineerInfo engineer(UUID id) {
        EngineerDto e = get("/api/v1/engineers/{id}", EngineerDto.class, id, "engineerId", "Инженер не найден");
        return new EngineerInfo(e.id(), e.keycloakUserId(), e.fullName(), e.position(), e.active());
    }

    @Override
    public ExecutorInfo executor(UUID id) {
        ExecutorDto e = get("/api/v1/executors/{id}", ExecutorDto.class, id, "executorId", "Исполнитель не найден");
        return new ExecutorInfo(e.id(), e.name(), e.shortName(), e.inn(), e.kpp(), e.address(), e.phone(), e.active());
    }

    @Override
    public ExecutorInfo defaultExecutor() {
        try {
            ExecutorDto e = client.get().uri("/api/v1/executors/default").retrieve().body(ExecutorDto.class);
            return e == null ? null
                    : new ExecutorInfo(e.id(), e.name(), e.shortName(), e.inn(), e.kpp(), e.address(), e.phone(), e.active());
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        }
    }

    @Override
    public SiteInfo site(UUID id) {
        SiteDto s = get("/api/v1/sites/{id}", SiteDto.class, id, "siteId", "Площадка не найдена");
        return new SiteInfo(s.id(), s.organizationId(), s.name(), s.code(), s.address(), s.active());
    }

    @Override
    public EngineerInfo currentEngineer() {
        try {
            EngineerDto e = client.get().uri("/api/v1/engineers/me").retrieve().body(EngineerDto.class);
            if (e == null) {
                throw new IllegalStateException("Пустой ответ справочника инженеров");
            }
            return new EngineerInfo(e.id(), e.keycloakUserId(), e.fullName(), e.position(), e.active());
        } catch (HttpClientErrorException.NotFound e) {
            throw new BusinessRuleException(
                    "Для вашей учётной записи нет карточки инженера. Попросите администратора создать её.");
        }
    }

    @Override
    public void raiseMeter(UUID equipmentId, BigDecimal hours, BigDecimal km) {
        if (hours == null && km == null) {
            return;
        }
        Map<String, Object> body = new HashMap<>();
        body.put("hours", hours);
        body.put("km", km);
        try {
            client.post().uri("/api/v1/equipment/{id}/meter", equipmentId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            log.warn("Не удалось обновить наработку техники {}: {}", equipmentId, e.getMessage());
        }
    }

    private <T> T get(String uri, Class<T> type, UUID id, String field, String notFound) {
        try {
            T result = client.get().uri(uri, id).retrieve().body(type);
            if (result == null) {
                throw new BusinessRuleException(field, notFound);
            }
            return result;
        } catch (HttpClientErrorException.NotFound e) {
            throw new BusinessRuleException(field, notFound);
        }
    }
}
