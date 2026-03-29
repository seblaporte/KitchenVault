package fr.seblaporte.mycookidoo.mapper;

import fr.seblaporte.mycookidoo.entity.SyncRun;
import fr.seblaporte.mycookidoo.generated.model.SyncRunDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Mapper(componentModel = "spring")
public interface SyncMapper {

    @Mapping(target = "startedAt", expression = "java(toOffsetDateTime(run.getStartedAt()))")
    @Mapping(target = "completedAt", expression = "java(toOffsetDateTime(run.getCompletedAt()))")
    @Mapping(target = "status", expression = "java(mapStatus(run.getStatus()))")
    SyncRunDto toDto(SyncRun run);

    default OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    default fr.seblaporte.mycookidoo.generated.model.SyncStatus mapStatus(
            fr.seblaporte.mycookidoo.entity.SyncStatus status) {
        return switch (status) {
            case RUNNING -> fr.seblaporte.mycookidoo.generated.model.SyncStatus.RUNNING;
            case SUCCESS -> fr.seblaporte.mycookidoo.generated.model.SyncStatus.SUCCESS;
            case PARTIAL -> fr.seblaporte.mycookidoo.generated.model.SyncStatus.PARTIAL;
            case FAILED -> fr.seblaporte.mycookidoo.generated.model.SyncStatus.FAILED;
        };
    }
}
