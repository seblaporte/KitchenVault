package fr.seblaporte.mycookidoo.mapper;

import fr.seblaporte.mycookidoo.generated.model.AdminStatsDto;
import fr.seblaporte.mycookidoo.service.StatsService;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Mapper(componentModel = "spring")
public interface StatsMapper {

    @Mapping(target = "lastSuccessfulSyncAt", expression = "java(toOffsetDateTime(stats.lastSuccessfulSyncAt()))")
    AdminStatsDto toDto(StatsService.AdminStats stats);

    default OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}
