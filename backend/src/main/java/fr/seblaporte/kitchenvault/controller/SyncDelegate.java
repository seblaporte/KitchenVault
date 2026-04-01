package fr.seblaporte.kitchenvault.controller;

import fr.seblaporte.kitchenvault.generated.api.SyncApiDelegate;
import fr.seblaporte.kitchenvault.generated.model.SyncRunDto;
import fr.seblaporte.kitchenvault.generated.model.SyncRunPageDto;
import fr.seblaporte.kitchenvault.mapper.SyncMapper;
import fr.seblaporte.kitchenvault.service.SyncRunService;
import fr.seblaporte.kitchenvault.service.SyncService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SyncDelegate implements SyncApiDelegate {

    private final SyncService syncService;
    private final SyncRunService syncRunService;
    private final SyncMapper syncMapper;

    public SyncDelegate(SyncService syncService, SyncRunService syncRunService, SyncMapper syncMapper) {
        this.syncService = syncService;
        this.syncRunService = syncRunService;
        this.syncMapper = syncMapper;
    }

    @Override
    public ResponseEntity<SyncRunDto> triggerSync() {
        try {
            var run = syncService.triggerSync();
            return ResponseEntity.accepted().body(syncMapper.toDto(run));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).build();
        }
    }

    @Override
    public ResponseEntity<SyncRunDto> getLatestSync() {
        return syncRunService.getLatestSync()
                .map(syncMapper::toDto)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Override
    public ResponseEntity<SyncRunPageDto> getSyncHistory(Integer page, Integer size) {
        int pageNum = page != null ? page : 0;
        int pageSize = size != null ? size : 20;
        Page<fr.seblaporte.kitchenvault.entity.SyncRun> resultPage =
                syncRunService.getSyncHistory(PageRequest.of(pageNum, pageSize));

        List<SyncRunDto> content = resultPage.getContent().stream()
                .map(syncMapper::toDto)
                .toList();

        SyncRunPageDto dto = new SyncRunPageDto();
        dto.setContent(content);
        dto.setTotalElements(resultPage.getTotalElements());
        dto.setTotalPages(resultPage.getTotalPages());
        dto.setPage(pageNum);
        dto.setSize(pageSize);

        return ResponseEntity.ok(dto);
    }
}
