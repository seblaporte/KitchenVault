package fr.seblaporte.kitchenvault.service;

import fr.seblaporte.kitchenvault.entity.SyncStatus;
import fr.seblaporte.kitchenvault.repository.SyncRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * A SyncRun left RUNNING at startup means the previous process died mid-sync (crash, redeploy,
 * kill -9) — nothing will ever mark it FAILED otherwise, permanently blocking both the scheduler
 * and the manual "sync now" button. Reconciles that state once the app is ready to serve traffic.
 */
@Component
public class SyncRunStartupReconciler {

    private static final Logger log = LoggerFactory.getLogger(SyncRunStartupReconciler.class);

    private final SyncRunRepository syncRunRepository;

    public SyncRunStartupReconciler(SyncRunRepository syncRunRepository) {
        this.syncRunRepository = syncRunRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void failLeftoverRunningSyncs() {
        syncRunRepository.findTopByStatusOrderByStartedAtDesc(SyncStatus.RUNNING)
                .ifPresent(run -> {
                    log.warn("Found a SyncRun stuck in RUNNING at startup (id={}, startedAt={}) — marking it FAILED",
                            run.getId(), run.getStartedAt());
                    run.fail("Interrompu par un redémarrage de l'application");
                    syncRunRepository.save(run);
                });
    }
}
