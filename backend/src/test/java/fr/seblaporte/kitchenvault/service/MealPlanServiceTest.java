package fr.seblaporte.kitchenvault.service;

import fr.seblaporte.kitchenvault.entity.MealPlanEntry;
import fr.seblaporte.kitchenvault.entity.MealType;
import fr.seblaporte.kitchenvault.entity.Recipe;
import fr.seblaporte.kitchenvault.repository.MealPlanEntryRepository;
import fr.seblaporte.kitchenvault.repository.RecipeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MealPlanServiceTest {

    @Mock MealPlanEntryRepository mealPlanEntryRepository;
    @Mock RecipeRepository recipeRepository;

    @InjectMocks MealPlanService mealPlanService;

    private static final LocalDate MONDAY = LocalDate.of(2024, 4, 1);

    @Test
    void upsertEntry_newEntry_createsWithSnapshot() {
        Recipe recipe = makeRecipe("r-1", "Tarte aux pommes");
        when(recipeRepository.findById("r-1")).thenReturn(Optional.of(recipe));
        when(mealPlanEntryRepository.findByEntryDateAndMealType(MONDAY, MealType.LUNCH)).thenReturn(Optional.empty());
        when(mealPlanEntryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MealPlanEntry result = mealPlanService.upsertEntry(MONDAY, MealType.LUNCH, "r-1");

        assertThat(result.getRecipe()).isEqualTo(recipe);
        assertThat(result.getRecipeNameSnapshot()).isEqualTo("Tarte aux pommes");
        assertThat(result.getRecipeIdSnapshot()).isEqualTo("r-1");
        assertThat(result.getMealType()).isEqualTo(MealType.LUNCH);
        verify(mealPlanEntryRepository).save(any());
    }

    @Test
    void upsertEntry_existingEntry_updatesSnapshot() {
        Recipe newRecipe = makeRecipe("r-2", "Gratin dauphinois");
        MealPlanEntry existing = new MealPlanEntry();
        existing.setEntryDate(MONDAY);
        existing.setMealType(MealType.LUNCH);
        existing.setRecipe(makeRecipe("r-1", "Old Recipe"));
        existing.setRecipeNameSnapshot("Old Recipe");

        when(recipeRepository.findById("r-2")).thenReturn(Optional.of(newRecipe));
        when(mealPlanEntryRepository.findByEntryDateAndMealType(MONDAY, MealType.LUNCH)).thenReturn(Optional.of(existing));
        when(mealPlanEntryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MealPlanEntry result = mealPlanService.upsertEntry(MONDAY, MealType.LUNCH, "r-2");

        assertThat(result.getRecipeNameSnapshot()).isEqualTo("Gratin dauphinois");
        assertThat(result.getRecipe()).isEqualTo(newRecipe);
    }

    @Test
    void upsertEntry_recipeNotFound_throwsNoSuchElementException() {
        when(recipeRepository.findById("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> mealPlanService.upsertEntry(MONDAY, MealType.LUNCH, "unknown"))
                .isInstanceOf(NoSuchElementException.class);

        verify(mealPlanEntryRepository, never()).save(any());
    }

    @Test
    void removeEntry_existingEntry_deletesIt() {
        MealPlanEntry entry = new MealPlanEntry();
        when(mealPlanEntryRepository.findByEntryDateAndMealType(MONDAY, MealType.DINNER)).thenReturn(Optional.of(entry));

        mealPlanService.removeEntry(MONDAY, MealType.DINNER);

        verify(mealPlanEntryRepository).delete(entry);
    }

    @Test
    void removeEntry_noEntry_doesNothing() {
        when(mealPlanEntryRepository.findByEntryDateAndMealType(MONDAY, MealType.DINNER)).thenReturn(Optional.empty());

        mealPlanService.removeEntry(MONDAY, MealType.DINNER);

        verify(mealPlanEntryRepository, never()).delete(any(MealPlanEntry.class));
    }

    @Test
    void relocateEntry_targetFree_movesEntryInPlace() {
        LocalDate target = MONDAY.plusDays(1);
        MealPlanEntry entry = new MealPlanEntry();
        entry.setEntryDate(MONDAY);
        entry.setMealType(MealType.LUNCH);

        when(mealPlanEntryRepository.findByEntryDateAndMealType(MONDAY, MealType.LUNCH)).thenReturn(Optional.of(entry));
        when(mealPlanEntryRepository.findByEntryDateAndMealType(target, MealType.DINNER)).thenReturn(Optional.empty());
        when(mealPlanEntryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MealPlanEntry result = mealPlanService.relocateEntry(MONDAY, MealType.LUNCH, target, MealType.DINNER);

        assertThat(result).isSameAs(entry);
        assertThat(result.getEntryDate()).isEqualTo(target);
        assertThat(result.getMealType()).isEqualTo(MealType.DINNER);
        verify(mealPlanEntryRepository).save(entry);
        verify(mealPlanEntryRepository, never()).delete(any(MealPlanEntry.class));
    }

    @Test
    void relocateEntry_sourceEmpty_throwsNoSuchElementException() {
        when(mealPlanEntryRepository.findByEntryDateAndMealType(MONDAY, MealType.LUNCH)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> mealPlanService.relocateEntry(MONDAY, MealType.LUNCH, MONDAY, MealType.DINNER))
                .isInstanceOf(NoSuchElementException.class);

        verify(mealPlanEntryRepository, never()).save(any());
    }

    @Test
    void relocateEntry_targetOccupied_throwsSlotOccupiedException() {
        LocalDate target = MONDAY.plusDays(1);
        MealPlanEntry source = new MealPlanEntry();
        source.setEntryDate(MONDAY);
        source.setMealType(MealType.LUNCH);
        MealPlanEntry occupyingTarget = new MealPlanEntry();

        when(mealPlanEntryRepository.findByEntryDateAndMealType(MONDAY, MealType.LUNCH)).thenReturn(Optional.of(source));
        when(mealPlanEntryRepository.findByEntryDateAndMealType(target, MealType.DINNER)).thenReturn(Optional.of(occupyingTarget));

        assertThatThrownBy(() -> mealPlanService.relocateEntry(MONDAY, MealType.LUNCH, target, MealType.DINNER))
                .isInstanceOf(MealPlanService.SlotOccupiedException.class);

        verify(mealPlanEntryRepository, never()).save(any());
    }

    @Test
    void relocateEntry_sameSlot_isNoOpAndReturnsEntryUnchanged() {
        MealPlanEntry entry = new MealPlanEntry();
        entry.setEntryDate(MONDAY);
        entry.setMealType(MealType.LUNCH);

        when(mealPlanEntryRepository.findByEntryDateAndMealType(MONDAY, MealType.LUNCH)).thenReturn(Optional.of(entry));

        MealPlanEntry result = mealPlanService.relocateEntry(MONDAY, MealType.LUNCH, MONDAY, MealType.LUNCH);

        assertThat(result).isSameAs(entry);
        verify(mealPlanEntryRepository, never()).save(any());
    }

    @Test
    void relocateUndefinedEntry_targetFree_movesSourceInPlace() {
        LocalDate target = MONDAY.plusDays(1);
        MealPlanEntry source = new MealPlanEntry();
        source.setId(10L);
        source.setEntryDate(MONDAY);
        source.setMealType(MealType.UNDEFINED);

        when(mealPlanEntryRepository.findById(10L)).thenReturn(Optional.of(source));
        when(mealPlanEntryRepository.findByEntryDateAndMealType(target, MealType.DINNER)).thenReturn(Optional.empty());
        when(mealPlanEntryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MealPlanEntry result = mealPlanService.relocateUndefinedEntry(10L, target, MealType.DINNER);

        assertThat(result).isSameAs(source);
        assertThat(result.getEntryDate()).isEqualTo(target);
        assertThat(result.getMealType()).isEqualTo(MealType.DINNER);
        verify(mealPlanEntryRepository, never()).saveAndFlush(any());
    }

    @Test
    void relocateUndefinedEntry_targetOccupied_displacesExistingEntryToUndefinedAtSourceDate() {
        LocalDate target = MONDAY.plusDays(1);
        MealPlanEntry source = new MealPlanEntry();
        source.setId(10L);
        source.setEntryDate(MONDAY);
        source.setMealType(MealType.UNDEFINED);

        MealPlanEntry occupying = new MealPlanEntry();
        occupying.setId(20L);
        occupying.setEntryDate(target);
        occupying.setMealType(MealType.DINNER);

        when(mealPlanEntryRepository.findById(10L)).thenReturn(Optional.of(source));
        when(mealPlanEntryRepository.findByEntryDateAndMealType(target, MealType.DINNER)).thenReturn(Optional.of(occupying));
        when(mealPlanEntryRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(mealPlanEntryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MealPlanEntry result = mealPlanService.relocateUndefinedEntry(10L, target, MealType.DINNER);

        assertThat(result.getEntryDate()).isEqualTo(target);
        assertThat(result.getMealType()).isEqualTo(MealType.DINNER);
        assertThat(occupying.getEntryDate()).isEqualTo(MONDAY);
        assertThat(occupying.getMealType()).isEqualTo(MealType.UNDEFINED);
        verify(mealPlanEntryRepository).saveAndFlush(occupying);
        verify(mealPlanEntryRepository).save(source);
    }

    @Test
    void relocateUndefinedEntry_unknownId_throwsNoSuchElementException() {
        when(mealPlanEntryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> mealPlanService.relocateUndefinedEntry(99L, MONDAY, MealType.LUNCH))
                .isInstanceOf(NoSuchElementException.class);

        verify(mealPlanEntryRepository, never()).save(any());
    }

    @Test
    void relocateUndefinedEntry_sourceNotUndefined_throwsNoSuchElementException() {
        MealPlanEntry source = new MealPlanEntry();
        source.setId(10L);
        source.setEntryDate(MONDAY);
        source.setMealType(MealType.LUNCH);
        when(mealPlanEntryRepository.findById(10L)).thenReturn(Optional.of(source));

        assertThatThrownBy(() -> mealPlanService.relocateUndefinedEntry(10L, MONDAY, MealType.DINNER))
                .isInstanceOf(NoSuchElementException.class);

        verify(mealPlanEntryRepository, never()).save(any());
    }

    @Test
    void suggest_withNoRecentRecipes_callsFindRandomRecipes() {
        List<Recipe> recipes = List.of(makeRecipe("r-1", "Tarte"), makeRecipe("r-2", "Quiche"));
        when(mealPlanEntryRepository.findRecentRecipeIds(any(), any())).thenReturn(List.of());
        when(recipeRepository.findRandomRecipes(null, 3)).thenReturn(recipes);

        List<Recipe> result = mealPlanService.suggest(MONDAY, MealType.LUNCH, null, 3);

        assertThat(result).hasSize(2);
        verify(recipeRepository).findRandomRecipes(null, 3);
        verify(recipeRepository, never()).findRandomRecipesExcluding(any(), any(), anyInt());
    }

    @Test
    void suggest_withRecentRecipes_excludesThem() {
        List<String> recentIds = List.of("r-1", "r-2");
        List<Recipe> suggestions = List.of(makeRecipe("r-3", "Soupe"));
        when(mealPlanEntryRepository.findRecentRecipeIds(any(), any())).thenReturn(recentIds);
        when(recipeRepository.findRandomRecipesExcluding(recentIds, null, 3)).thenReturn(suggestions);

        List<Recipe> result = mealPlanService.suggest(MONDAY, MealType.LUNCH, null, 3);

        assertThat(result).hasSize(1);
        verify(recipeRepository).findRandomRecipesExcluding(recentIds, null, 3);
    }

    @Test
    void suggest_withMaxTimeFilter_passesItToRepository() {
        when(mealPlanEntryRepository.findRecentRecipeIds(any(), any())).thenReturn(List.of());
        when(recipeRepository.findRandomRecipes(30, 3)).thenReturn(List.of());

        mealPlanService.suggest(MONDAY, MealType.LUNCH, 30, 3);

        verify(recipeRepository).findRandomRecipes(eq(30), eq(3));
    }

    @Test
    void suggest_withRecentRecipesAndMaxTime_passesFiltersToRepository() {
        List<String> recentIds = List.of("r-old");
        when(mealPlanEntryRepository.findRecentRecipeIds(any(), any())).thenReturn(recentIds);
        when(recipeRepository.findRandomRecipesExcluding(recentIds, 45, 3)).thenReturn(List.of());

        mealPlanService.suggest(MONDAY, MealType.DINNER, 45, 3);

        verify(recipeRepository).findRandomRecipesExcluding(recentIds, 45, 3);
        verify(recipeRepository, never()).findRandomRecipes(any(), anyInt());
    }

    private Recipe makeRecipe(String id, String name) {
        Recipe r = new Recipe(id);
        r.setName(name);
        return r;
    }
}
