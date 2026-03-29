package fr.seblaporte.mycookidoo.controller;

import fr.seblaporte.mycookidoo.generated.api.AdminApiDelegate;
import fr.seblaporte.mycookidoo.generated.model.AdminStatsDto;
import fr.seblaporte.mycookidoo.mapper.StatsMapper;
import fr.seblaporte.mycookidoo.service.StatsService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class AdminDelegate implements AdminApiDelegate {

    private final StatsService statsService;
    private final StatsMapper statsMapper;

    public AdminDelegate(StatsService statsService, StatsMapper statsMapper) {
        this.statsService = statsService;
        this.statsMapper = statsMapper;
    }

    @Override
    public ResponseEntity<AdminStatsDto> getAdminStats() {
        return ResponseEntity.ok(statsMapper.toDto(statsService.getAdminStats()));
    }
}
