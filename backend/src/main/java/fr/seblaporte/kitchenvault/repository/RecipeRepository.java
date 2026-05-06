package fr.seblaporte.kitchenvault.repository;

import fr.seblaporte.kitchenvault.entity.Recipe;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RecipeRepository extends JpaRepository<Recipe, String>, JpaSpecificationExecutor<Recipe> {

    Page<Recipe> findByNameContainingIgnoreCase(String name, Pageable pageable);

    @Query("SELECT r FROM Recipe r WHERE r.lastSyncedAt < :threshold")
    List<Recipe> findOutdatedRecipes(@Param("threshold") Instant threshold);

    @Query(value = "SELECT * FROM recipe r WHERE (:maxTime IS NULL OR r.total_time_minutes <= :maxTime) ORDER BY RANDOM() LIMIT :count", nativeQuery = true)
    List<Recipe> findRandomRecipes(@Param("maxTime") Integer maxTime, @Param("count") int count);

    @Query(value = "SELECT * FROM recipe r WHERE r.id NOT IN :excludedIds AND (:maxTime IS NULL OR r.total_time_minutes <= :maxTime) ORDER BY RANDOM() LIMIT :count", nativeQuery = true)
    List<Recipe> findRandomRecipesExcluding(@Param("excludedIds") List<String> excludedIds, @Param("maxTime") Integer maxTime, @Param("count") int count);

    @Query("SELECT r FROM Recipe r LEFT JOIN FETCH r.ingredientGroups ig LEFT JOIN FETCH ig.ingredients WHERE r.id = :id")
    Optional<Recipe> findByIdWithIngredients(@Param("id") String id);
}
