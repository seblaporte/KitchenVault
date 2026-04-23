package fr.seblaporte.kitchenvault.repository;

import fr.seblaporte.kitchenvault.entity.MealPlanEntry;
import fr.seblaporte.kitchenvault.entity.MealType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MealPlanEntryRepository extends JpaRepository<MealPlanEntry, Long> {

    @Query("SELECT e FROM MealPlanEntry e LEFT JOIN FETCH e.recipe WHERE e.entryDate BETWEEN :from AND :to ORDER BY e.entryDate ASC, e.mealType ASC")
    List<MealPlanEntry> findWeekPlan(@Param("from") LocalDate from, @Param("to") LocalDate to);

    Optional<MealPlanEntry> findByEntryDateAndMealType(LocalDate date, MealType mealType);

    @Query("SELECT e FROM MealPlanEntry e LEFT JOIN FETCH e.recipe WHERE e.recipe.id = :recipeId ORDER BY e.entryDate DESC")
    List<MealPlanEntry> findByRecipeIdOrderByEntryDateDesc(@Param("recipeId") String recipeId, Pageable pageable);

    @Query("SELECT DISTINCT e.recipe.id FROM MealPlanEntry e WHERE e.entryDate BETWEEN :from AND :to AND e.recipe IS NOT NULL")
    List<String> findRecentRecipeIds(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
