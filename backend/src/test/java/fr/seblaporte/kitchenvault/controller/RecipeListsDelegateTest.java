package fr.seblaporte.kitchenvault.controller;

import fr.seblaporte.kitchenvault.entity.RecipeListSettings;
import fr.seblaporte.kitchenvault.generated.api.RecipeListsApiController;
import fr.seblaporte.kitchenvault.generated.model.RecipeListMembershipDto;
import fr.seblaporte.kitchenvault.generated.model.RecipeListOverviewDto;
import fr.seblaporte.kitchenvault.mapper.RecipeListMapper;
import fr.seblaporte.kitchenvault.service.RecipeListService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {RecipeListsApiController.class})
@Import(RecipeListsDelegate.class)
class RecipeListsDelegateTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean RecipeListService recipeListService;
    @MockitoBean RecipeListMapper recipeListMapper;

    @Test
    void getRecipeListsOverview_returnsThreeLists() throws Exception {
        when(recipeListService.getSettings()).thenReturn(List.of(
                new RecipeListSettings(fr.seblaporte.kitchenvault.entity.RecipeListRole.FAVORITES)));
        when(recipeListService.getRecipesForRole(any())).thenReturn(List.of());
        when(recipeListMapper.toOverviewDto(any(), any())).thenReturn(
                new RecipeListOverviewDto(fr.seblaporte.kitchenvault.generated.model.RecipeListRole.FAVORITES,
                        "Favoris", List.of()));

        mockMvc.perform(get("/api/v1/recipe-lists"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].role").value("FAVORITES"));
    }

    @Test
    void getRecipeListMembership_recipeNotFound_returnsNotFound() throws Exception {
        when(recipeListService.recipeExists("missing")).thenReturn(false);

        mockMvc.perform(get("/api/v1/recipes/missing/list-membership"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getRecipeListMembership_returnsCurrentRole() throws Exception {
        when(recipeListService.recipeExists("r-1")).thenReturn(true);
        when(recipeListService.getRoleOfRecipe("r-1"))
                .thenReturn(Optional.of(fr.seblaporte.kitchenvault.entity.RecipeListRole.DISCOVERY));
        RecipeListMembershipDto dto = new RecipeListMembershipDto();
        dto.setRole(fr.seblaporte.kitchenvault.generated.model.RecipeListRole.DISCOVERY);
        when(recipeListMapper.toMembershipDto(fr.seblaporte.kitchenvault.entity.RecipeListRole.DISCOVERY))
                .thenReturn(dto);

        mockMvc.perform(get("/api/v1/recipes/r-1/list-membership"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("DISCOVERY"));
    }

    @Test
    void updateRecipeListMembership_missingRole_returnsBadRequest() throws Exception {
        mockMvc.perform(put("/api/v1/recipes/r-1/list-membership")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateRecipeListMembership_recipeNotFound_returnsNotFound() throws Exception {
        when(recipeListService.moveRecipe("missing", fr.seblaporte.kitchenvault.entity.RecipeListRole.FAVORITES))
                .thenThrow(new NoSuchElementException("Recipe not found"));

        mockMvc.perform(put("/api/v1/recipes/missing/list-membership")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"FAVORITES\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateRecipeListMembership_success_returnsUpdatedMembership() throws Exception {
        when(recipeListService.moveRecipe("r-1", fr.seblaporte.kitchenvault.entity.RecipeListRole.FAVORITES))
                .thenReturn(fr.seblaporte.kitchenvault.entity.RecipeListRole.FAVORITES);
        RecipeListMembershipDto dto = new RecipeListMembershipDto();
        dto.setRole(fr.seblaporte.kitchenvault.generated.model.RecipeListRole.FAVORITES);
        when(recipeListMapper.toMembershipDto(fr.seblaporte.kitchenvault.entity.RecipeListRole.FAVORITES))
                .thenReturn(dto);

        mockMvc.perform(put("/api/v1/recipes/r-1/list-membership")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"FAVORITES\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("FAVORITES"));
    }

    @Test
    void updateRecipeListMembership_collectionNotBound_returnsUnprocessableEntity() throws Exception {
        when(recipeListService.moveRecipe("r-1", fr.seblaporte.kitchenvault.entity.RecipeListRole.FAVORITES))
                .thenThrow(new RecipeListService.CollectionNotBoundException("Aucune collection rattachée"));

        mockMvc.perform(put("/api/v1/recipes/r-1/list-membership")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"FAVORITES\"}"))
                .andExpect(status().isUnprocessableEntity());
    }
}
