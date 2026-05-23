package fr.seblaporte.kitchenvault.service;

import fr.seblaporte.kitchenvault.cookidoo.CookidooServiceClient;
import fr.seblaporte.kitchenvault.cookidoo.model.AddRecipesToCalendarRequest;
import fr.seblaporte.kitchenvault.entity.MealPlanEntry;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CookidooCalendarSyncService {

    private final MealPlanService mealPlanService;
    private final CookidooServiceClient cookidooServiceClient;

    public CookidooCalendarSyncService(MealPlanService mealPlanService, CookidooServiceClient cookidooServiceClient) {
        this.mealPlanService = mealPlanService;
        this.cookidooServiceClient = cookidooServiceClient;
    }

    public void syncWeek(LocalDate weekStart, boolean replace) {
        List<MealPlanEntry> entries = mealPlanService.getWeekPlan(weekStart);

        Map<LocalDate, List<String>> byDate = entries.stream()
                .filter(e -> e.getRecipeIdSnapshot() != null && !e.getRecipeIdSnapshot().isBlank())
                .collect(Collectors.groupingBy(
                        MealPlanEntry::getEntryDate,
                        Collectors.mapping(MealPlanEntry::getRecipeIdSnapshot, Collectors.toList())
                ));

        for (Map.Entry<LocalDate, List<String>> dayEntry : byDate.entrySet()) {
            cookidooServiceClient.addRecipesToCalendar(
                    dayEntry.getKey().format(DateTimeFormatter.ISO_LOCAL_DATE),
                    new AddRecipesToCalendarRequest(dayEntry.getValue(), replace)
            );
        }
    }
}
