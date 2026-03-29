package fr.seblaporte.mycookidoo.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sync_run")
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

    protected SyncRun() {}

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

    public UUID getId() { return id; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public SyncStatus getStatus() { return status; }
    public Integer getCollectionsSynced() { return collectionsSynced; }
    public Integer getRecipesSynced() { return recipesSynced; }
    public String getErrorMessage() { return errorMessage; }
    public boolean isRunning() { return status == SyncStatus.RUNNING; }
}
