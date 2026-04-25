package fr.seblaporte.kitchenvault.ai.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import fr.seblaporte.kitchenvault.entity.MealPlanEntry;
import fr.seblaporte.kitchenvault.entity.MealType;
import fr.seblaporte.kitchenvault.repository.RecipeRepository;
import fr.seblaporte.kitchenvault.service.MealPlanService;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Component
public class MealPlanTool {

    public record WeekPlanningResult(
            int assignedCount,
            int skippedCount,
            List<String> assignments,
            List<String> warnings
    ) {}

    private final MealPlanService mealPlanService;
    private final RecipeRepository recipeRepository;

    public MealPlanTool(MealPlanService mealPlanService, RecipeRepository recipeRepository) {
        this.mealPlanService = mealPlanService;
        this.recipeRepository = recipeRepository;
    }

    @Tool("""
            Planifie une recette pour un repas précis (déjeuner ou dîner) à une date donnée.
            weekStart doit être un lundi au format YYYY-MM-DD.
            dayOfWeek doit être MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY ou SUNDAY.
            mealType doit être LUNCH ou DINNER.
            Utiliser les recipeId obtenus via RecipeSuggestionTool.
            """)
    public String planMeal(
            @P("Date du lundi de la semaine (YYYY-MM-DD)") String weekStart,
            @P("Jour de la semaine en anglais majuscules (ex: MONDAY)") String dayOfWeek,
            @P("Type de repas : LUNCH ou DINNER") String mealType,
            @P("Identifiant de la recette à planifier") String recipeId
    ) {
        LocalDate monday = parseAndValidateMonday(weekStart);
        LocalDate entryDate = monday.with(DayOfWeek.valueOf(dayOfWeek.toUpperCase()));
        MealType type = parseMealType(mealType);

        if (!recipeRepository.existsById(recipeId)) {
            return "Recette introuvable : " + recipeId + ". Utilisez RecipeSuggestionTool pour trouver l'ID correct.";
        }

        MealPlanEntry entry = mealPlanService.upsertEntry(entryDate, type, recipeId);
        return "Planifié : " + entry.getRecipeNameSnapshot() + " le " + entryDate + " (" + type + ")";
    }

    @Tool("""
            Affiche le plan de repas de la semaine (14 créneaux : lundi à dimanche, midi et soir).
            weekStart doit être un lundi au format YYYY-MM-DD.
            """)
    public String getCurrentWeekPlan(
            @P("Date du lundi de la semaine (YYYY-MM-DD)") String weekStart
    ) {
        LocalDate monday = parseAndValidateMonday(weekStart);
        List<MealPlanEntry> entries = mealPlanService.getWeekPlan(monday);

        if (entries.isEmpty()) {
            return "Aucun repas planifié pour la semaine du " + monday + ".";
        }

        StringBuilder sb = new StringBuilder("Plan de la semaine du ").append(monday).append(":\n");
        for (MealPlanEntry entry : entries) {
            sb.append("- ").append(entry.getEntryDate())
                    .append(" ").append(entry.getMealType())
                    .append(" : ").append(entry.getRecipeNameSnapshot())
                    .append("\n");
        }
        return sb.toString();
    }

    @Tool("""
            Planifie automatiquement toute une semaine de repas à partir d'une liste de recettes candidates.
            weekStart doit être un lundi au format YYYY-MM-DD.
            constraints décrit les préférences (ex: 'repas rapides en semaine, festif le weekend').
            overwrite=true remplace les créneaux déjà occupés, overwrite=false les ignore.
            """)
    public WeekPlanningResult planWeekWithConstraints(
            @P("Date du lundi de la semaine (YYYY-MM-DD)") String weekStart,
            @P("Contraintes et préférences pour la planification") String constraints,
            @P("Liste des identifiants de recettes candidates") List<String> candidateRecipeIds,
            @P("Remplacer les créneaux déjà occupés ?") boolean overwrite
    ) {
        LocalDate monday = parseAndValidateMonday(weekStart);
        List<MealPlanEntry> existing = mealPlanService.getWeekPlan(monday);

        int assigned = 0;
        int skipped = 0;
        List<String> assignments = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> usedRecipeIds = new ArrayList<>();

        DayOfWeek[] days = DayOfWeek.values();
        MealType[] mealTypes = { MealType.LUNCH, MealType.DINNER };

        int recipeIndex = 0;
        for (DayOfWeek day : days) {
            for (MealType meal : mealTypes) {
                LocalDate date = monday.with(day);
                boolean alreadyPlanned = existing.stream()
                        .anyMatch(e -> e.getEntryDate().equals(date) && e.getMealType() == meal);

                if (alreadyPlanned && !overwrite) {
                    skipped++;
                    continue;
                }

                String nextRecipeId = findNextUnusedRecipe(candidateRecipeIds, usedRecipeIds, recipeIndex);
                if (nextRecipeId == null) {
                    warnings.add(day + " " + meal + " : plus de recettes candidates disponibles");
                    skipped++;
                    continue;
                }

                if (!recipeRepository.existsById(nextRecipeId)) {
                    warnings.add("Recette introuvable ignorée : " + nextRecipeId);
                    recipeIndex++;
                    skipped++;
                    continue;
                }

                try {
                    MealPlanEntry entry = mealPlanService.upsertEntry(date, meal, nextRecipeId);
                    assignments.add(day + " " + meal + " → " + entry.getRecipeNameSnapshot());
                    usedRecipeIds.add(nextRecipeId);
                    assigned++;
                    recipeIndex++;
                } catch (Exception e) {
                    warnings.add("Erreur lors de la planification de " + day + " " + meal + " : " + e.getMessage());
                    skipped++;
                }
            }
        }

        return new WeekPlanningResult(assigned, skipped, assignments, warnings);
    }

    private String findNextUnusedRecipe(List<String> candidates, List<String> used, int startIndex) {
        for (int i = startIndex; i < candidates.size(); i++) {
            if (!used.contains(candidates.get(i))) {
                return candidates.get(i);
            }
        }
        return null;
    }

    private LocalDate parseAndValidateMonday(String weekStart) {
        try {
            LocalDate date = LocalDate.parse(weekStart, DateTimeFormatter.ISO_LOCAL_DATE);
            if (date.getDayOfWeek() != DayOfWeek.MONDAY) {
                throw new IllegalArgumentException(
                        "weekStart doit être un lundi (YYYY-MM-DD), reçu : " + weekStart + " (" + date.getDayOfWeek() + ")");
            }
            return date;
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Format de date invalide, attendu YYYY-MM-DD, reçu : " + weekStart);
        }
    }

    private MealType parseMealType(String mealType) {
        try {
            return MealType.valueOf(mealType.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("mealType doit être LUNCH ou DINNER, reçu : " + mealType);
        }
    }
}
