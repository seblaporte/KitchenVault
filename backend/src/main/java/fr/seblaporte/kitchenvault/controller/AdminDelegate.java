package fr.seblaporte.kitchenvault.controller;

import fr.seblaporte.kitchenvault.generated.api.AdminApiDelegate;
import fr.seblaporte.kitchenvault.generated.model.AdminStatsDto;
import fr.seblaporte.kitchenvault.mapper.StatsMapper;
import fr.seblaporte.kitchenvault.service.StatsService;
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
