package fr.seblaporte.mycookidoo.repository;

import fr.seblaporte.mycookidoo.entity.SyncRun;
import fr.seblaporte.mycookidoo.entity.SyncStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SyncRunRepository extends JpaRepository<SyncRun, UUID> {

    Optional<SyncRun> findTopByOrderByStartedAtDesc();

    Optional<SyncRun> findTopByStatusOrderByStartedAtDesc(SyncStatus status);

    boolean existsByStatus(SyncStatus status);

    Page<SyncRun> findAllByOrderByStartedAtDesc(Pageable pageable);
}
