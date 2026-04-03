package fr.seblaporte.kitchenvault.controller;

import fr.seblaporte.kitchenvault.entity.SyncRun;
import fr.seblaporte.kitchenvault.generated.api.SyncApiController;
import fr.seblaporte.kitchenvault.generated.model.SyncRunDto;
import fr.seblaporte.kitchenvault.generated.model.SyncStatus;
import fr.seblaporte.kitchenvault.mapper.SyncMapper;
import fr.seblaporte.kitchenvault.service.SyncRunService;
import fr.seblaporte.kitchenvault.service.SyncService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {SyncApiController.class})
@Import(SyncDelegate.class)
class SyncControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean SyncService syncService;
    @MockitoBean SyncRunService syncRunService;
    @MockitoBean SyncMapper syncMapper;

    @Test
    void triggerSync_returnsAccepted() throws Exception {
        SyncRun run = SyncRun.start();
        SyncRunDto dto = makeSyncRunDto(SyncStatus.RUNNING);

        when(syncService.triggerSync()).thenReturn(run);
        when(syncMapper.toDto(run)).thenReturn(dto);

        mockMvc.perform(post("/api/v1/sync"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("RUNNING"));
    }

    @Test
    void triggerSync_whenAlreadyRunning_returnsConflict() throws Exception {
        when(syncService.triggerSync()).thenThrow(new IllegalStateException("already running"));

        mockMvc.perform(post("/api/v1/sync"))
                .andExpect(status().isConflict());
    }

    @Test
    void getLatestSync_whenExists_returnsOk() throws Exception {
        SyncRun run = SyncRun.start();
        SyncRunDto dto = makeSyncRunDto(SyncStatus.SUCCESS);

        when(syncRunService.getLatestSync()).thenReturn(Optional.of(run));
        when(syncMapper.toDto(run)).thenReturn(dto);

        mockMvc.perform(get("/api/v1/sync/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    @Test
    void getLatestSync_whenNone_returnsNotFound() throws Exception {
        when(syncRunService.getLatestSync()).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/sync/latest"))
                .andExpect(status().isNotFound());
    }

    private SyncRunDto makeSyncRunDto(SyncStatus status) {
        SyncRunDto dto = new SyncRunDto();
        dto.setId(UUID.randomUUID());
        dto.setStartedAt(OffsetDateTime.now());
        dto.setStatus(status);
        return dto;
    }
}
