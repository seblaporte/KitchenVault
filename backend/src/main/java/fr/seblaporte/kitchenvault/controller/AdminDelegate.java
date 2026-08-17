package fr.seblaporte.kitchenvault.controller;

import fr.seblaporte.kitchenvault.entity.RecipeListSettings;
import fr.seblaporte.kitchenvault.generated.api.AdminApiDelegate;
import fr.seblaporte.kitchenvault.generated.model.AdminStatsDto;
import fr.seblaporte.kitchenvault.generated.model.CreateRecipeListCollectionDto;
import fr.seblaporte.kitchenvault.generated.model.RecipeListRole;
import fr.seblaporte.kitchenvault.generated.model.RecipeListSettingsDto;
import fr.seblaporte.kitchenvault.generated.model.RecipeListSettingsUpdateDto;
import fr.seblaporte.kitchenvault.mapper.RecipeListMapper;
import fr.seblaporte.kitchenvault.mapper.StatsMapper;
import fr.seblaporte.kitchenvault.service.RecipeListService;
import fr.seblaporte.kitchenvault.service.StatsService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AdminDelegate implements AdminApiDelegate {

    private final StatsService statsService;
    private final StatsMapper statsMapper;
    private final RecipeListService recipeListService;
    private final RecipeListMapper recipeListMapper;

    public AdminDelegate(StatsService statsService, StatsMapper statsMapper,
                          RecipeListService recipeListService, RecipeListMapper recipeListMapper) {
        this.statsService = statsService;
        this.statsMapper = statsMapper;
        this.recipeListService = recipeListService;
        this.recipeListMapper = recipeListMapper;
    }

    @Override
    public ResponseEntity<AdminStatsDto> getAdminStats() {
        return ResponseEntity.ok(statsMapper.toDto(statsService.getAdminStats()));
    }

    @Override
    public ResponseEntity<List<RecipeListSettingsDto>> getRecipeListSettings() {
        return ResponseEntity.ok(recipeListMapper.toDtoList(recipeListService.getSettings()));
    }

    @Override
    public ResponseEntity<RecipeListSettingsDto> updateRecipeListSettings(
            RecipeListRole role, RecipeListSettingsUpdateDto recipeListSettingsUpdateDto) {
        var entityRole = fr.seblaporte.kitchenvault.entity.RecipeListRole.valueOf(role.name());

        RecipeListSettings settings = recipeListService.getSettings(entityRole);
        if (recipeListSettingsUpdateDto.getDisplayLabel() != null) {
            settings = recipeListService.updateLabel(entityRole, recipeListSettingsUpdateDto.getDisplayLabel());
        }
        if (recipeListSettingsUpdateDto.getCollectionId() != null) {
            settings = recipeListService.bindExistingCollection(
                    entityRole, recipeListSettingsUpdateDto.getCollectionId());
        }
        return ResponseEntity.ok(recipeListMapper.toDto(settings));
    }

    @Override
    public ResponseEntity<RecipeListSettingsDto> createRecipeListCollection(
            RecipeListRole role, CreateRecipeListCollectionDto createRecipeListCollectionDto) {
        var entityRole = fr.seblaporte.kitchenvault.entity.RecipeListRole.valueOf(role.name());
        RecipeListSettings settings = recipeListService.createAndBindCollection(
                entityRole, createRecipeListCollectionDto.getName());
        return ResponseEntity.ok(recipeListMapper.toDto(settings));
    }
}
