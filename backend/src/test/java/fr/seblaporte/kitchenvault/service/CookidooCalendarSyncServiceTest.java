package fr.seblaporte.kitchenvault.service;

import fr.seblaporte.kitchenvault.cookidoo.CookidooServiceClient;
import fr.seblaporte.kitchenvault.cookidoo.model.AddRecipesToCalendarRequest;
import fr.seblaporte.kitchenvault.entity.MealPlanEntry;
import fr.seblaporte.kitchenvault.entity.MealType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CookidooCalendarSyncServiceTest {

    @Mock
    MealPlanService mealPlanService;

    @Mock
    CookidooServiceClient cookidooServiceClient;

    @InjectMocks
    CookidooCalendarSyncService service;

    private static final LocalDate MONDAY = LocalDate.of(2025, 5, 19);
    private static final LocalDate TUESDAY = LocalDate.of(2025, 5, 20);

    private MealPlanEntry entry(LocalDate date, MealType mealType, String recipeId) {
        MealPlanEntry e = new MealPlanEntry();
        e.setEntryDate(date);
        e.setMealType(mealType);
        e.setRecipeIdSnapshot(recipeId);
        e.setRecipeNameSnapshot("Recipe " + recipeId);
        return e;
    }

    @Test
    void syncWeek_withEntries_callsClientOncePerDistinctDate() {
        MealPlanEntry lunch = entry(MONDAY, MealType.LUNCH, "r-1");
        MealPlanEntry dinner = entry(MONDAY, MealType.DINNER, "r-2");
        MealPlanEntry tuesday = entry(TUESDAY, MealType.LUNCH, "r-3");

        when(mealPlanService.getWeekPlan(MONDAY)).thenReturn(List.of(lunch, dinner, tuesday));

        service.syncWeek(MONDAY, false);

        verify(cookidooServiceClient, times(2)).addRecipesToCalendar(any(), any());
        verify(cookidooServiceClient).addRecipesToCalendar(eq("2025-05-19"), any());
        verify(cookidooServiceClient).addRecipesToCalendar(eq("2025-05-20"), any());
    }

    @Test
    void syncWeek_withEntries_sendsCorrectRecipeIds() {
        MealPlanEntry lunch = entry(MONDAY, MealType.LUNCH, "r-1");
        MealPlanEntry dinner = entry(MONDAY, MealType.DINNER, "r-2");

        when(mealPlanService.getWeekPlan(MONDAY)).thenReturn(List.of(lunch, dinner));

        service.syncWeek(MONDAY, false);

        ArgumentCaptor<AddRecipesToCalendarRequest> captor = ArgumentCaptor.forClass(AddRecipesToCalendarRequest.class);
        verify(cookidooServiceClient).addRecipesToCalendar(eq("2025-05-19"), captor.capture());

        AddRecipesToCalendarRequest request = captor.getValue();
        assertThat(request.recipeIds()).containsExactlyInAnyOrder("r-1", "r-2");
        assertThat(request.replace()).isFalse();
    }

    @Test
    void syncWeek_emptyWeek_noCallToClient() {
        when(mealPlanService.getWeekPlan(MONDAY)).thenReturn(List.of());

        service.syncWeek(MONDAY, false);

        verifyNoInteractions(cookidooServiceClient);
    }

    @Test
    void syncWeek_withReplaceTrue_setsReplaceFieldTrue() {
        MealPlanEntry lunch = entry(MONDAY, MealType.LUNCH, "r-1");
        when(mealPlanService.getWeekPlan(MONDAY)).thenReturn(List.of(lunch));

        service.syncWeek(MONDAY, true);

        ArgumentCaptor<AddRecipesToCalendarRequest> captor = ArgumentCaptor.forClass(AddRecipesToCalendarRequest.class);
        verify(cookidooServiceClient).addRecipesToCalendar(eq("2025-05-19"), captor.capture());

        assertThat(captor.getValue().replace()).isTrue();
    }

    @Test
    void syncWeek_entriesWithBlankRecipeId_filtered() {
        MealPlanEntry valid = entry(MONDAY, MealType.LUNCH, "r-1");
        MealPlanEntry blankId = entry(MONDAY, MealType.DINNER, "   ");
        MealPlanEntry nullId = new MealPlanEntry();
        nullId.setEntryDate(MONDAY);
        nullId.setMealType(MealType.DINNER);
        nullId.setRecipeIdSnapshot(null);
        nullId.setRecipeNameSnapshot("unknown");

        when(mealPlanService.getWeekPlan(MONDAY)).thenReturn(List.of(valid, blankId, nullId));

        service.syncWeek(MONDAY, false);

        ArgumentCaptor<AddRecipesToCalendarRequest> captor = ArgumentCaptor.forClass(AddRecipesToCalendarRequest.class);
        verify(cookidooServiceClient, times(1)).addRecipesToCalendar(eq("2025-05-19"), captor.capture());

        assertThat(captor.getValue().recipeIds()).containsExactly("r-1");
    }
}
