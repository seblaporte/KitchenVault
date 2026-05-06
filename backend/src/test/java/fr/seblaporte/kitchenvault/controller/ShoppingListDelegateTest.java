package fr.seblaporte.kitchenvault.controller;

import fr.seblaporte.kitchenvault.entity.ShoppingCategory;
import fr.seblaporte.kitchenvault.entity.ShoppingListItem;
import fr.seblaporte.kitchenvault.generated.api.ShoppingListApiController;
import fr.seblaporte.kitchenvault.service.ShoppingListService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {ShoppingListApiController.class})
@Import(ShoppingListDelegate.class)
class ShoppingListDelegateTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean ShoppingListService shoppingListService;

    // ─── GET /api/v1/shopping-list ────────────────────────────────────────────

    @Test
    void getShoppingList_emptyList_returns200WithEmptyArrays() throws Exception {
        when(shoppingListService.getList()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/shopping-list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.addedRecipeIds").isArray())
                .andExpect(jsonPath("$.addedRecipeIds").isEmpty());
    }

    @Test
    void getShoppingList_withNonCustomItem_populatesAddedRecipeIds() throws Exception {
        ShoppingListItem item = makeItem("carottes", "300g", ShoppingCategory.PRODUCE, "[\"r-1\"]", false, false);
        when(shoppingListService.getList()).thenReturn(List.of(item));

        mockMvc.perform(get("/api/v1/shopping-list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].name").value("carottes"))
                .andExpect(jsonPath("$.items[0].category").value("produce"))
                .andExpect(jsonPath("$.addedRecipeIds[0]").value("r-1"));
    }

    // ─── POST /api/v1/shopping-list/recipes/{recipeId} ────────────────────────

    @Test
    void addRecipeToShoppingList_validRecipe_returns200WithConsolidatedList() throws Exception {
        ShoppingListItem item = makeItem("pommes", "4 pièces", ShoppingCategory.PRODUCE, "[\"r-1\"]", false, false);
        when(shoppingListService.addRecipe("r-1")).thenReturn(List.of(item));

        mockMvc.perform(post("/api/v1/shopping-list/recipes/r-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].name").value("pommes"));
    }

    @Test
    void addRecipeToShoppingList_recipeNotFound_returns404() throws Exception {
        when(shoppingListService.addRecipe("unknown"))
                .thenThrow(new NoSuchElementException("Recipe not found"));

        mockMvc.perform(post("/api/v1/shopping-list/recipes/unknown"))
                .andExpect(status().isNotFound());
    }

    @Test
    void addRecipeToShoppingList_agentError_returns503() throws Exception {
        when(shoppingListService.addRecipe("r-1"))
                .thenThrow(new RuntimeException("AI service unavailable"));

        mockMvc.perform(post("/api/v1/shopping-list/recipes/r-1"))
                .andExpect(status().isServiceUnavailable());
    }

    // ─── DELETE /api/v1/shopping-list/recipes/{recipeId} ─────────────────────

    @Test
    void removeRecipeFromShoppingList_validRecipe_returns200WithUpdatedList() throws Exception {
        when(shoppingListService.removeRecipe("r-1")).thenReturn(List.of());

        mockMvc.perform(delete("/api/v1/shopping-list/recipes/r-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void removeRecipeFromShoppingList_notInList_returns404() throws Exception {
        when(shoppingListService.removeRecipe("r-unknown"))
                .thenThrow(new NoSuchElementException("Not found"));

        mockMvc.perform(delete("/api/v1/shopping-list/recipes/r-unknown"))
                .andExpect(status().isNotFound());
    }

    // ─── POST /api/v1/shopping-list/items ────────────────────────────────────

    @Test
    void addCustomShoppingListItem_valid_returns201WithCustomItem() throws Exception {
        ShoppingListItem customItem = makeItem("papier toilette", null, ShoppingCategory.OTHER, "[]", false, true);
        when(shoppingListService.addCustomItem(eq("papier toilette"), any()))
                .thenReturn(customItem);

        mockMvc.perform(post("/api/v1/shopping-list/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"papier toilette\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("papier toilette"))
                .andExpect(jsonPath("$.custom").value(true))
                .andExpect(jsonPath("$.category").value("other"));
    }

    // ─── PATCH /api/v1/shopping-list/items/{itemId} ───────────────────────────

    @Test
    void toggleShoppingListItem_validId_returns200WithToggledItem() throws Exception {
        UUID id = UUID.randomUUID();
        ShoppingListItem toggled = makeItem("carottes", "300g", ShoppingCategory.PRODUCE, "[]", true, false);
        when(shoppingListService.toggleItem(id)).thenReturn(toggled);

        mockMvc.perform(patch("/api/v1/shopping-list/items/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checked").value(true));
    }

    @Test
    void toggleShoppingListItem_notFound_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(shoppingListService.toggleItem(id))
                .thenThrow(new NoSuchElementException("Not found"));

        mockMvc.perform(patch("/api/v1/shopping-list/items/" + id))
                .andExpect(status().isNotFound());
    }

    // ─── DELETE /api/v1/shopping-list/items/{itemId} ─────────────────────────

    @Test
    void deleteShoppingListItem_validId_returns204() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/shopping-list/items/" + id))
                .andExpect(status().isNoContent());

        verify(shoppingListService).deleteItem(id);
    }

    @Test
    void deleteShoppingListItem_notFound_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(shoppingListService.toggleItem(id))
                .thenThrow(new NoSuchElementException("Not found"));

        // deleteItem throws NoSuchElementException
        org.mockito.Mockito.doThrow(new NoSuchElementException("Not found"))
                .when(shoppingListService).deleteItem(id);

        mockMvc.perform(delete("/api/v1/shopping-list/items/" + id))
                .andExpect(status().isNotFound());
    }

    // ─── DELETE /api/v1/shopping-list/checked ────────────────────────────────

    @Test
    void clearCheckedItems_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/shopping-list/checked"))
                .andExpect(status().isNoContent());

        verify(shoppingListService).clearChecked();
    }

    // ─── DELETE /api/v1/shopping-list ────────────────────────────────────────

    @Test
    void clearShoppingList_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/shopping-list"))
                .andExpect(status().isNoContent());

        verify(shoppingListService).clearAll();
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private ShoppingListItem makeItem(String name, String quantity, ShoppingCategory category,
                                      String sourceRecipeIds, boolean checked, boolean custom) {
        ShoppingListItem item = new ShoppingListItem();
        item.setName(name);
        item.setQuantity(quantity);
        item.setCategory(category);
        item.setSourceRecipeIds(sourceRecipeIds);
        item.setChecked(checked);
        item.setCustom(custom);

        // Simulate @PrePersist timestamps
        Instant now = Instant.now();
        try {
            java.lang.reflect.Field createdAt = ShoppingListItem.class.getDeclaredField("createdAt");
            java.lang.reflect.Field updatedAt = ShoppingListItem.class.getDeclaredField("updatedAt");
            createdAt.setAccessible(true);
            updatedAt.setAccessible(true);
            createdAt.set(item, now);
            updatedAt.set(item, now);
        } catch (Exception ignored) {}

        return item;
    }
}
