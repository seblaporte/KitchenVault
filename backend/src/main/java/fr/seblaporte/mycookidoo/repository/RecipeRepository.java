package fr.seblaporte.mycookidoo.repository;

import fr.seblaporte.mycookidoo.entity.Recipe;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface RecipeRepository extends JpaRepository<Recipe, String>, JpaSpecificationExecutor<Recipe> {

    Page<Recipe> findByNameContainingIgnoreCase(String name, Pageable pageable);

    @Query("SELECT r FROM Recipe r WHERE r.lastSyncedAt < :threshold")
    List<Recipe> findOutdatedRecipes(@Param("threshold") Instant threshold);
}
