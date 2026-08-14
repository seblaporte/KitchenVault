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

        // En mode "écraser", il faut repasser sur les 7 jours de la semaine (y compris ceux
        // devenus vides côté KitchenVault) pour que le microservice purge aussi ces jours sur
        // Cookidoo. Sinon les jours retirés du plan gardent leurs anciennes recettes.
        List<LocalDate> datesToSync = replace
                ? weekStart.datesUntil(weekStart.plusDays(7)).toList()
                : List.copyOf(byDate.keySet());

        for (LocalDate date : datesToSync) {
            cookidooServiceClient.addRecipesToCalendar(
                    date.format(DateTimeFormatter.ISO_LOCAL_DATE),
                    new AddRecipesToCalendarRequest(byDate.getOrDefault(date, List.of()), replace)
            );
        }
    }
}
