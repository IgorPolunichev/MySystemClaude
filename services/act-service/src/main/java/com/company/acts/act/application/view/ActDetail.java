package com.company.acts.act.application.view;

import com.company.acts.act.domain.ActStatus;
import com.company.acts.act.domain.MeterType;
import com.company.acts.act.domain.ParticipantRole;
import com.company.acts.act.domain.PartSource;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/** Карточка акта целиком: шапка, участники, время, запчасти, итоги и права текущего пользователя. */
public record ActDetail(
        UUID id,
        Long version,
        String number,
        ActStatus status,
        Organization organization,
        Equipment equipment,
        BigDecimal meterHours,
        BigDecimal meterKm,
        String location,
        LocalDate workStartDate,
        LocalDate workEndDate,
        boolean periodOverridden,
        long calendarDays,
        long workedDays,
        String shortDescription,
        String detailedDescription,
        String recommendations,
        String customerRepName,
        String customerRepPosition,
        boolean hideRequisites,
        boolean hideTravelTime,
        UUID authorEngineerId,
        Instant createdAt,
        Instant updatedAt,
        Instant closedAt,
        String closedBy,
        List<ParticipantView> engineers,
        List<TimeEntryView> timeEntries,
        List<PartLineView> parts,
        BigDecimal totalHours,
        BigDecimal totalTravelHours,
        List<String> missingForClose,
        Permissions permissions) {

    public record Organization(UUID id, String name, String shortName, String inn, String kpp, String legalAddress,
                               UUID departmentId, String departmentName) {
    }

    public record Equipment(UUID id, String typeName, String brand, String model, String serialNumber,
                            String garageNumber, String plateNumber, MeterType meterType) {
    }

    public record ParticipantView(UUID engineerId, String fullName, String position, ParticipantRole role,
                                  BigDecimal totalHours, boolean isCurrentUser) {
    }

    /** Ячейка табеля: интервал, перерывы и рассчитанное продуктивное время. */
    public record TimeEntryView(UUID id, UUID engineerId, LocalDate workDate,
                                @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm") LocalTime startTime,
                                @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm") LocalTime endTime,
                                List<BreakView> breaks, int workMinutes, BigDecimal hours, BigDecimal travelHours,
                                String comment) {
    }

    public record BreakView(@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm") LocalTime startTime,
                            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm") LocalTime endTime) {
    }

    public record PartLineView(UUID id, int position, PartSource source, UUID catalogItemId, String name,
                               String catalogNumber, BigDecimal quantity, String unitCode, String unitName,
                               String notes) {
    }

    /** Что можно текущему пользователю — интерфейс показывает только доступные действия. */
    public record Permissions(boolean canEdit, boolean canEditAnyTime, boolean canOpen, boolean canClose,
                              boolean canReopen, boolean canCancel, boolean canDelete, boolean requiresAdminComment) {
    }
}
