package fr.seblaporte.kitchenvault.controller;

import fr.seblaporte.kitchenvault.entity.RecipeListSettings;
import fr.seblaporte.kitchenvault.generated.api.AdminApiController;
import fr.seblaporte.kitchenvault.generated.model.RecipeListSettingsDto;
import fr.seblaporte.kitchenvault.mapper.RecipeListMapper;
import fr.seblaporte.kitchenvault.mapper.StatsMapper;
import fr.seblaporte.kitchenvault.service.RecipeListService;
import fr.seblaporte.kitchenvault.service.StatsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {AdminApiController.class})
@Import(AdminDelegate.class)
class AdminDelegateTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean StatsService statsService;
    @MockitoBean StatsMapper statsMapper;
    @MockitoBean RecipeListService recipeListService;
    @MockitoBean RecipeListMapper recipeListMapper;

    @Test
    void updateRecipeListSettings_updatesLabelOnly() throws Exception {
        RecipeListSettings current = new RecipeListSettings(fr.seblaporte.kitchenvault.entity.RecipeListRole.FAVORITES);
        RecipeListSettings updated = new RecipeListSettings(fr.seblaporte.kitchenvault.entity.RecipeListRole.FAVORITES);
        updated.setDisplayLabel("Mes classiques");

        when(recipeListService.getSettings(fr.seblaporte.kitchenvault.entity.RecipeListRole.FAVORITES)).thenReturn(current);
        when(recipeListService.updateLabel(fr.seblaporte.kitchenvault.entity.RecipeListRole.FAVORITES, "Mes classiques"))
                .thenReturn(updated);
        when(recipeListMapper.toDto(updated)).thenReturn(
                new RecipeListSettingsDto(fr.seblaporte.kitchenvault.generated.model.RecipeListRole.FAVORITES, "Mes classiques"));

        mockMvc.perform(patch("/api/v1/admin/recipe-lists/FAVORITES")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayLabel\":\"Mes classiques\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayLabel").value("Mes classiques"));

        verify(recipeListService).updateLabel(fr.seblaporte.kitchenvault.entity.RecipeListRole.FAVORITES, "Mes classiques");
    }

    @Test
    void updateRecipeListSettings_duplicateBinding_returnsConflict() throws Exception {
        when(recipeListService.getSettings(any())).thenReturn(
                new RecipeListSettings(fr.seblaporte.kitchenvault.entity.RecipeListRole.FAVORITES));
        when(recipeListService.bindExistingCollection(any(), any()))
                .thenThrow(new RecipeListService.DuplicateBindingException("Déjà rattachée"));

        mockMvc.perform(patch("/api/v1/admin/recipe-lists/FAVORITES")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"collectionId\":\"col-1\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void createRecipeListCollection_createsAndBinds() throws Exception {
        RecipeListSettings settings = new RecipeListSettings(fr.seblaporte.kitchenvault.entity.RecipeListRole.DISCOVERY);
        settings.setDisplayLabel("Miam");
        when(recipeListService.createAndBindCollection(
                fr.seblaporte.kitchenvault.entity.RecipeListRole.DISCOVERY, "Nouvelle collection"))
                .thenReturn(settings);
        when(recipeListMapper.toDto(settings)).thenReturn(
                new RecipeListSettingsDto(fr.seblaporte.kitchenvault.generated.model.RecipeListRole.DISCOVERY, "Miam"));

        mockMvc.perform(post("/api/v1/admin/recipe-lists/DISCOVERY/collection")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Nouvelle collection\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayLabel").value("Miam"));
    }
}
