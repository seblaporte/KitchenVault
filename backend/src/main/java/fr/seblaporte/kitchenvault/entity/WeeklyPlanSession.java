package fr.seblaporte.kitchenvault.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.lang.Nullable;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "weekly_plan_session")
@Getter
@Setter
@NoArgsConstructor
public class WeeklyPlanSession {

    @Id
    @Column(name = "session_id", nullable = false, length = 255)
    private String sessionId;

    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart;

    @Column(name = "initial_done", nullable = false)
    private boolean initialDone = false;

    @Nullable
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "pending_changes", columnDefinition = "jsonb")
    private String pendingChanges;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_active_at", nullable = false)
    private Instant lastActiveAt;

    public WeeklyPlanSession(String sessionId, LocalDate weekStart) {
        this.sessionId = sessionId;
        this.weekStart = weekStart;
        this.createdAt = Instant.now();
        this.lastActiveAt = Instant.now();
    }
}
