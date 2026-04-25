package fr.seblaporte.kitchenvault.repository;

import fr.seblaporte.kitchenvault.entity.SeasonalVegetable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SeasonalVegetableRepository extends JpaRepository<SeasonalVegetable, Long> {

    @Query(value = "SELECT * FROM seasonal_vegetable WHERE :month = ANY(months)", nativeQuery = true)
    List<SeasonalVegetable> findByMonth(@Param("month") int month);
}
