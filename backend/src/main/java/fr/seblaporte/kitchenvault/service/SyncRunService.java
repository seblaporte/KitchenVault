package fr.seblaporte.kitchenvault.service;

import fr.seblaporte.kitchenvault.entity.SyncRun;
import fr.seblaporte.kitchenvault.repository.SyncRunRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class SyncRunService {

    private final SyncRunRepository syncRunRepository;

    public SyncRunService(SyncRunRepository syncRunRepository) {
        this.syncRunRepository = syncRunRepository;
    }

    public Optional<SyncRun> getLatestSync() {
        return syncRunRepository.findTopByOrderByStartedAtDesc();
    }

    public Page<SyncRun> getSyncHistory(Pageable pageable) {
        return syncRunRepository.findAllByOrderByStartedAtDesc(pageable);
    }
}
