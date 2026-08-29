package fr.seblaporte.kitchenvault.repository;

import fr.seblaporte.kitchenvault.entity.Collection;
import fr.seblaporte.kitchenvault.entity.CollectionType;
import fr.seblaporte.kitchenvault.entity.RecipeListRole;
import fr.seblaporte.kitchenvault.entity.RecipeListSettings;
import org.assertj.db.type.Table;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RecipeListSettingsRepositoryIT {

    @Autowired RecipeListSettingsRepository recipeListSettingsRepository;
    @Autowired CollectionRepository collectionRepository;
    @Autowired DataSource dataSource;

    @BeforeEach
    void cleanUp() {
        // The 3 roles are seeded by Liquibase and are never deleted — only unbind any
        // collection left over by a previous test before clearing the collections themselves.
        for (RecipeListSettings settings : recipeListSettingsRepository.findAll()) {
            settings.setCollection(null);
            recipeListSettingsRepository.save(settings);
        }
        collectionRepository.deleteAll();
    }

    @Test
    void seedData_hasThreeRolesWithNeutralLabelsAndNoCollection() {
        org.assertj.db.api.Assertions.assertThat(new Table(dataSource, "recipe_list_settings"))
                .hasNumberOfRows(3);

        assertThat(recipeListSettingsRepository.findById(RecipeListRole.FAVORITES))
                .isPresent()
                .get()
                .satisfies(settings -> {
                    assertThat(settings.getDisplayLabel()).isNotBlank();
                    assertThat(settings.getCollection()).isNull();
                });
        assertThat(recipeListSettingsRepository.findById(RecipeListRole.DISCOVERY)).isPresent();
        assertThat(recipeListSettingsRepository.findById(RecipeListRole.REJECTED)).isPresent();
    }

    @Test
    void findByCollectionId_returnsSettingsBoundToThatCollection() {
        Collection collection = newCollection("col-1");
        collectionRepository.save(collection);

        RecipeListSettings settings = recipeListSettingsRepository.findById(RecipeListRole.DISCOVERY).orElseThrow();
        settings.setCollection(collection);
        settings.setUpdatedAt(Instant.now());
        recipeListSettingsRepository.save(settings);

        Optional<RecipeListSettings> found = recipeListSettingsRepository.findByCollectionId("col-1");
        assertThat(found).isPresent();
        assertThat(found.get().getRole()).isEqualTo(RecipeListRole.DISCOVERY);
    }

    @Test
    void findByCollectionId_returnsEmptyWhenUnbound() {
        assertThat(recipeListSettingsRepository.findByCollectionId("unknown-id")).isEmpty();
    }

    @Test
    void collectionId_mustBeUniqueAcrossRoles() {
        Collection collection = newCollection("col-shared");
        collectionRepository.save(collection);

        RecipeListSettings favorites = recipeListSettingsRepository.findById(RecipeListRole.FAVORITES).orElseThrow();
        favorites.setCollection(collection);
        favorites.setUpdatedAt(Instant.now());
        recipeListSettingsRepository.saveAndFlush(favorites);

        RecipeListSettings discovery = recipeListSettingsRepository.findById(RecipeListRole.DISCOVERY).orElseThrow();
        discovery.setCollection(collection);
        discovery.setUpdatedAt(Instant.now());

        assertThatThrownBy(() -> recipeListSettingsRepository.saveAndFlush(discovery))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Collection newCollection(String id) {
        Collection collection = new Collection(id);
        collection.setName("Collection " + id);
        collection.setType(CollectionType.CUSTOM);
        collection.setLastSyncedAt(Instant.now());
        return collection;
    }
}
