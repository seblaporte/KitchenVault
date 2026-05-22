package fr.seblaporte.kitchenvault.controller;

import fr.seblaporte.kitchenvault.entity.Recipe;
import fr.seblaporte.kitchenvault.entity.ShoppingList;
import fr.seblaporte.kitchenvault.entity.ShoppingListItem;
import fr.seblaporte.kitchenvault.entity.ShoppingListRecipe;
import fr.seblaporte.kitchenvault.generated.api.ShoppingListApiController;
import fr.seblaporte.kitchenvault.generated.model.AddShoppingListItemRequest;
import fr.seblaporte.kitchenvault.generated.model.ShoppingCategory;
import fr.seblaporte.kitchenvault.generated.model.ShoppingListDto;
import fr.seblaporte.kitchenvault.generated.model.ShoppingListItemDto;
import fr.seblaporte.kitchenvault.generated.model.ShoppingListRecipeDto;
import fr.seblaporte.kitchenvault.mapper.ShoppingListMapper;
import fr.seblaporte.kitchenvault.service.ShoppingListService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {ShoppingListApiController.class})
@Import(ShoppingListDelegate.class)
class ShoppingListDelegateTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean ShoppingListService shoppingListService;
    @MockitoBean ShoppingListMapper shoppingListMapper;

    // ─── GET /api/v1/shopping-list ────────────────────────────────────────────

    @Test
    void getShoppingList_returnsDto() throws Exception {
        ShoppingList list = new ShoppingList();
        ShoppingListDto dto = emptyListDto();
        when(shoppingListService.getShoppingList()).thenReturn(list);
        when(shoppingListMapper.toDto(list)).thenReturn(dto);

        mockMvc.perform(get("/api/v1/shopping-list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipes").isArray())
                .andExpect(jsonPath("$.items").isArray());
    }

    // ─── DELETE /api/v1/shopping-list ─────────────────────────────────────────

    @Test
    void clearShoppingList_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/shopping-list"))
                .andExpect(status().isNoContent());

        verify(shoppingListService).clearList();
    }

    // ─── POST /api/v1/shopping-list/selection/recipes/{recipeId} ─────────────

    @Test
    void addRecipeToSelection_whenRecipeExists_returns200() throws Exception {
        ShoppingList list = new ShoppingList();
        Recipe recipe = makeRecipe("r-1", "Tarte");
        ShoppingListRecipe selection = new ShoppingListRecipe(list, recipe);

        ShoppingListRecipeDto dto = new ShoppingListRecipeDto()
                .recipeId("r-1")
                .recipeName("Tarte")
                .recipeIdSnapshot("r-1")
                .consolidated(false)
                .addedAt(OffsetDateTime.now(ZoneOffset.UTC));

        when(shoppingListService.addRecipeToSelection("r-1")).thenReturn(selection);
        when(shoppingListMapper.toRecipeDto(selection)).thenReturn(dto);

        mockMvc.perform(post("/api/v1/shopping-list/selection/recipes/r-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipeName").value("Tarte"))
                .andExpect(jsonPath("$.consolidated").value(false));
    }

    @Test
    void addRecipeToSelection_whenRecipeNotFound_returns404() throws Exception {
        when(shoppingListService.addRecipeToSelection("unknown"))
                .thenThrow(new NoSuchElementException("Recipe not found: unknown"));

        mockMvc.perform(post("/api/v1/shopping-list/selection/recipes/unknown"))
                .andExpect(status().isNotFound());
    }

    // ─── DELETE /api/v1/shopping-list/selection/recipes/{recipeId} ───────────

    @Test
    void removeRecipeFromSelection_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/shopping-list/selection/recipes/r-1"))
                .andExpect(status().isNoContent());

        verify(shoppingListService).removeRecipeFromSelection("r-1");
    }

    // ─── POST /api/v1/shopping-list/consolidate ───────────────────────────────

    @Test
    void consolidateShoppingList_returns200WithItems() throws Exception {
        ShoppingList list = new ShoppingList();
        ShoppingListDto dto = new ShoppingListDto(List.of(), List.of(
                new ShoppingListItemDto()
                        .id(UUID.randomUUID())
                        .name("Carottes")
                        .quantity("500g")
                        .category(ShoppingCategory.PRODUCE)
                        .checked(false)
                        .sourceRecipeIds(List.of("r-1"))
                        .custom(false)
                        .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
        ));

        when(shoppingListService.consolidate()).thenReturn(list);
        when(shoppingListMapper.toDto(list)).thenReturn(dto);

        mockMvc.perform(post("/api/v1/shopping-list/consolidate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].name").value("Carottes"))
                .andExpect(jsonPath("$.items[0].category").value("PRODUCE"));
    }

    @Test
    void consolidateShoppingList_whenEmptySelection_returns422() throws Exception {
        when(shoppingListService.consolidate())
                .thenThrow(new ShoppingListService.EmptySelectionException("Aucune recette dans la sélection."));

        mockMvc.perform(post("/api/v1/shopping-list/consolidate"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Aucune recette dans la sélection."));
    }

    // ─── POST /api/v1/shopping-list/items ─────────────────────────────────────

    @Test
    void addShoppingListItem_returns201WithItem() throws Exception {
        ShoppingList list = new ShoppingList();
        ShoppingListItem item = new ShoppingListItem(list, "Pain bio", "1 baguette", fr.seblaporte.kitchenvault.entity.ShoppingCategory.OTHER, List.of(), Map.of(), 0);
        item.setCustom(true);

        ShoppingListItemDto dto = new ShoppingListItemDto()
                .id(UUID.randomUUID())
                .name("Pain bio")
                .quantity("1 baguette")
                .category(ShoppingCategory.OTHER)
                .checked(false)
                .sourceRecipeIds(List.of())
                .custom(true)
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC));

        when(shoppingListService.addCustomItem("Pain bio", "1 baguette")).thenReturn(item);
        when(shoppingListMapper.toItemDto(item)).thenReturn(dto);

        mockMvc.perform(post("/api/v1/shopping-list/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Pain bio\",\"quantity\":\"1 baguette\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Pain bio"))
                .andExpect(jsonPath("$.custom").value(true));
    }

    @Test
    void addShoppingListItem_withNullQuantity_returns201() throws Exception {
        ShoppingList list = new ShoppingList();
        ShoppingListItem item = new ShoppingListItem(list, "Miel", null, fr.seblaporte.kitchenvault.entity.ShoppingCategory.OTHER, List.of(), Map.of(), 0);
        item.setCustom(true);

        ShoppingListItemDto dto = new ShoppingListItemDto()
                .id(UUID.randomUUID())
                .name("Miel")
                .category(ShoppingCategory.OTHER)
                .checked(false)
                .sourceRecipeIds(List.of())
                .custom(true)
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC));

        when(shoppingListService.addCustomItem(eq("Miel"), any())).thenReturn(item);
        when(shoppingListMapper.toItemDto(item)).thenReturn(dto);

        mockMvc.perform(post("/api/v1/shopping-list/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Miel\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Miel"));
    }

    // ─── PATCH /api/v1/shopping-list/items/{id}/toggle ───────────────────────

    @Test
    void toggleShoppingListItem_returns200WithUpdatedState() throws Exception {
        UUID itemId = UUID.randomUUID();
        ShoppingList list = new ShoppingList();
        ShoppingListItem item = new ShoppingListItem(list, "Carottes", "500g", fr.seblaporte.kitchenvault.entity.ShoppingCategory.PRODUCE, List.of(), Map.of(), 0);
        item.setChecked(true);

        ShoppingListItemDto dto = new ShoppingListItemDto()
                .id(itemId)
                .name("Carottes")
                .category(ShoppingCategory.PRODUCE)
                .checked(true)
                .sourceRecipeIds(List.of())
                .custom(false)
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC));

        when(shoppingListService.toggleItem(itemId)).thenReturn(item);
        when(shoppingListMapper.toItemDto(item)).thenReturn(dto);

        mockMvc.perform(patch("/api/v1/shopping-list/items/{id}/toggle", itemId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checked").value(true));
    }

    @Test
    void toggleShoppingListItem_whenNotFound_returns404() throws Exception {
        UUID itemId = UUID.randomUUID();
        when(shoppingListService.toggleItem(itemId))
                .thenThrow(new NoSuchElementException("Shopping list item not found: " + itemId));

        mockMvc.perform(patch("/api/v1/shopping-list/items/{id}/toggle", itemId))
                .andExpect(status().isNotFound());
    }

    // ─── DELETE /api/v1/shopping-list/items/{id} ──────────────────────────────

    @Test
    void deleteShoppingListItem_returns204() throws Exception {
        UUID itemId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/shopping-list/items/{id}", itemId))
                .andExpect(status().isNoContent());

        verify(shoppingListService).deleteItem(itemId);
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private ShoppingListDto emptyListDto() {
        return new ShoppingListDto(List.of(), List.of());
    }

    private Recipe makeRecipe(String id, String name) {
        Recipe recipe = new Recipe(id);
        recipe.setName(name);
        return recipe;
    }
}
