package fr.seblaporte.mycookidoo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sync_run")
@Getter
@NoArgsConstructor
public class SyncRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SyncStatus status;

    @Column(name = "collections_synced")
    private Integer collectionsSynced;

    @Column(name = "recipes_synced")
    private Integer recipesSynced;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    public static SyncRun start() {
        SyncRun run = new SyncRun();
        run.startedAt = Instant.now();
        run.status = SyncStatus.RUNNING;
        return run;
    }

    public void complete(int collectionsSynced, int recipesSynced) {
        this.completedAt = Instant.now();
        this.status = SyncStatus.SUCCESS;
        this.collectionsSynced = collectionsSynced;
        this.recipesSynced = recipesSynced;
    }

    public void fail(String errorMessage) {
        this.completedAt = Instant.now();
        this.status = SyncStatus.FAILED;
        this.errorMessage = errorMessage;
    }

    public void partialSuccess(int collectionsSynced, int recipesSynced, String errorMessage) {
        this.completedAt = Instant.now();
        this.status = SyncStatus.PARTIAL;
        this.collectionsSynced = collectionsSynced;
        this.recipesSynced = recipesSynced;
        this.errorMessage = errorMessage;
    }

    public boolean isRunning() {
        return status == SyncStatus.RUNNING;
    }
}
