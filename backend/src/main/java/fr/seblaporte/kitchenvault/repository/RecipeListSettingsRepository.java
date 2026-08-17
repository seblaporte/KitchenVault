package fr.seblaporte.kitchenvault.repository;

import fr.seblaporte.kitchenvault.entity.RecipeListRole;
import fr.seblaporte.kitchenvault.entity.RecipeListSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RecipeListSettingsRepository extends JpaRepository<RecipeListSettings, RecipeListRole> {

    Optional<RecipeListSettings> findByCollectionId(String collectionId);
}
