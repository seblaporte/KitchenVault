package fr.seblaporte.kitchenvault.service;

import fr.seblaporte.kitchenvault.entity.SyncRun;
import fr.seblaporte.kitchenvault.entity.SyncStatus;
import fr.seblaporte.kitchenvault.repository.SyncRunRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SyncRunStartupReconcilerTest {

    @Mock SyncRunRepository syncRunRepository;

    @InjectMocks SyncRunStartupReconciler reconciler;

    @Test
    void failLeftoverRunningSyncs_whenRunningRowExists_marksItFailed() {
        SyncRun stuck = SyncRun.start();
        when(syncRunRepository.findTopByStatusOrderByStartedAtDesc(SyncStatus.RUNNING))
                .thenReturn(Optional.of(stuck));

        reconciler.failLeftoverRunningSyncs();

        assertThat(stuck.getStatus()).isEqualTo(SyncStatus.FAILED);
        verify(syncRunRepository).save(stuck);
    }

    @Test
    void failLeftoverRunningSyncs_whenNoRunningRow_doesNothing() {
        when(syncRunRepository.findTopByStatusOrderByStartedAtDesc(SyncStatus.RUNNING))
                .thenReturn(Optional.empty());

        reconciler.failLeftoverRunningSyncs();

        verify(syncRunRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
