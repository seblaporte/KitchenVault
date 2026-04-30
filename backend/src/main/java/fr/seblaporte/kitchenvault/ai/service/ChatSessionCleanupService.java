package fr.seblaporte.kitchenvault.ai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class ChatSessionCleanupService {

    private static final Logger log = LoggerFactory.getLogger(ChatSessionCleanupService.class);

    private final JdbcTemplate jdbc;

    @Value("${ai.session.retention-days:30}")
    private int retentionDays;

    public ChatSessionCleanupService(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Scheduled(cron = "${ai.session.cleanup-cron:0 0 2 * * *}")
    @Transactional
    public void purgeExpiredSessions() {
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);

        int messages = jdbc.update(
                "DELETE FROM chat_message WHERE session_id IN " +
                "(SELECT session_id FROM weekly_plan_session WHERE last_active_at < ?)",
                cutoff);

        int sessions = jdbc.update(
                "DELETE FROM weekly_plan_session WHERE last_active_at < ?",
                cutoff);

        // Also purge chat_message rows for recipe sessions (no weekly_plan_session row)
        int orphanMessages = jdbc.update(
                "DELETE FROM chat_message WHERE created_at < ?",
                cutoff);

        log.info("Session cleanup: deleted {} sessions, {} weekly plan messages, {} orphan recipe messages (cutoff: {} days)",
                sessions, messages, orphanMessages, retentionDays);
    }
}
