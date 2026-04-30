package fr.seblaporte.kitchenvault.repository;

import fr.seblaporte.kitchenvault.entity.WeeklyPlanSession;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WeeklyPlanSessionRepository extends JpaRepository<WeeklyPlanSession, String> {
}
