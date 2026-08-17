package fr.seblaporte.kitchenvault.controller;

import fr.seblaporte.kitchenvault.generated.api.RecipeListsApiDelegate;
import fr.seblaporte.kitchenvault.generated.model.RecipeListMembershipDto;
import fr.seblaporte.kitchenvault.generated.model.RecipeListOverviewDto;
import fr.seblaporte.kitchenvault.mapper.RecipeListMapper;
import fr.seblaporte.kitchenvault.service.RecipeListService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.NoSuchElementException;

@Component
public class RecipeListsDelegate implements RecipeListsApiDelegate {

    private final RecipeListService recipeListService;
    private final RecipeListMapper recipeListMapper;

    public RecipeListsDelegate(RecipeListService recipeListService, RecipeListMapper recipeListMapper) {
        this.recipeListService = recipeListService;
        this.recipeListMapper = recipeListMapper;
    }

    @Override
    public ResponseEntity<List<RecipeListOverviewDto>> getRecipeListsOverview() {
        List<RecipeListOverviewDto> overview = recipeListService.getSettings().stream()
                .map(settings -> recipeListMapper.toOverviewDto(
                        settings, recipeListService.getRecipesForRole(settings.getRole())))
                .toList();
        return ResponseEntity.ok(overview);
    }

    @Override
    public ResponseEntity<RecipeListMembershipDto> getRecipeListMembership(String id) {
        if (!recipeListService.recipeExists(id)) {
            return ResponseEntity.notFound().build();
        }
        var role = recipeListService.getRoleOfRecipe(id).orElse(null);
        return ResponseEntity.ok(recipeListMapper.toMembershipDto(role));
    }

    @Override
    public ResponseEntity<RecipeListMembershipDto> updateRecipeListMembership(
            String id, RecipeListMembershipDto recipeListMembershipDto) {
        if (recipeListMembershipDto.getRole() == null) {
            return ResponseEntity.badRequest().build();
        }
        try {
            var targetRole = fr.seblaporte.kitchenvault.entity.RecipeListRole.valueOf(
                    recipeListMembershipDto.getRole().name());
            var newRole = recipeListService.moveRecipe(id, targetRole);
            return ResponseEntity.ok(recipeListMapper.toMembershipDto(newRole));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
