package fr.seblaporte.kitchenvault.ai.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import fr.seblaporte.kitchenvault.ai.service.UnitNormalizationService;
import fr.seblaporte.kitchenvault.ai.service.UnitNormalizationService.QuantityUnit;
import fr.seblaporte.kitchenvault.config.AiProperties;
import fr.seblaporte.kitchenvault.entity.Ingredient;
import fr.seblaporte.kitchenvault.entity.MealPlanEntry;
import fr.seblaporte.kitchenvault.entity.Recipe;
import fr.seblaporte.kitchenvault.service.MealPlanService;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ShoppingListTool {

    public record ShoppingItem(String ingredientName, String quantity, boolean incompatibleUnits) {}

    public record ShoppingCategory(String name, List<ShoppingItem> items) {}

    public record ShoppingListResult(String weekStart, List<ShoppingCategory> categories, List<String> warnings) {}

    private static final Map<String, String> KEYWORD_CATEGORY = new LinkedHashMap<>();

    static {
        KEYWORD_CATEGORY.put("tomate|courgette|aubergine|poivron|carotte|poireau|oignon|ail|pomme de terre|épinard|brocoli|chou|laitue|salade|haricot|pois|asperge|artichaut|champignon|céleri|navet|radis|betterave|concombre|fenouil|persil|basilic|ciboulette|coriandre|thym|laurier|romarin|menthe", "Légumes et herbes");
        KEYWORD_CATEGORY.put("pomme|poire|banane|citron|orange|raisin|fraise|framboise|abricot|pêche|prune|melon|pastèque|ananas|mangue|fruit", "Fruits");
        KEYWORD_CATEGORY.put("poulet|bœuf|veau|agneau|porc|canard|saumon|thon|cabillaud|crevette|moule|poisson|viande|jambon|lardons|bacon", "Viandes et poissons");
        KEYWORD_CATEGORY.put("lait|crème|beurre|fromage|yaourt|œuf|gruyère|parmesan|camembert|mozzarella|ricotta|feta", "Produits laitiers");
        KEYWORD_CATEGORY.put("riz|pâtes|farine|pain|semoule|quinoa|lentille|pois chiche|haricot blanc|boulgour|avoine|blé|maïs|féculent", "Féculents");
        KEYWORD_CATEGORY.put("sel|poivre|huile|vinaigre|sucre|moutarde|ketchup|sauce|épice|cumin|paprika|cannelle|noix de muscade|curry|curcuma|gingembre|levure|bicarbonate", "Épices et condiments");
    }

    private static final Pattern QUANTITY_PATTERN = Pattern.compile("^([\\d.,]+)\\s*([a-zA-Zàâéèêëîïôùûüç.éèàù/-]+)?");

    private final MealPlanService mealPlanService;
    private final UnitNormalizationService normalizationService;
    private final AiProperties aiProperties;

    public ShoppingListTool(
            MealPlanService mealPlanService,
            UnitNormalizationService normalizationService,
            AiProperties aiProperties) {
        this.mealPlanService = mealPlanService;
        this.normalizationService = normalizationService;
        this.aiProperties = aiProperties;
    }

    @Tool("""
            Génère la liste de courses pour une semaine à partir des recettes planifiées.
            weekStart doit être un lundi au format YYYY-MM-DD.
            Regroupe les ingrédients identiques, agrège les quantités si possible.
            """)
    public ShoppingListResult generateShoppingList(
            @P("Date du lundi de la semaine (YYYY-MM-DD)") String weekStart,
            @P("true pour inclure sel, poivre, huile et autres produits de base") boolean includeBasics
    ) {
        LocalDate monday = parseMonday(weekStart);
        List<MealPlanEntry> entries = mealPlanService.getWeekPlan(monday);
        List<String> warnings = new ArrayList<>();

        if (entries.isEmpty()) {
            return new ShoppingListResult(weekStart, List.of(), List.of("Aucun repas planifié pour cette semaine."));
        }

        Map<String, List<QuantityUnit>> ingredientQuantities = new LinkedHashMap<>();

        for (MealPlanEntry entry : entries) {
            Recipe recipe = entry.getRecipe();
            if (recipe == null) continue;

            recipe.getIngredientGroups().forEach(group ->
                    group.getIngredients().forEach(ingredient -> {
                        String name = ingredient.getName().trim().toLowerCase();
                        if (!includeBasics && isBasic(name)) return;

                        QuantityUnit qu = parseDescription(ingredient.getDescription());
                        ingredientQuantities.computeIfAbsent(name, k -> new ArrayList<>()).add(qu);
                    })
            );
        }

        Map<String, List<ShoppingItem>> byCategory = new LinkedHashMap<>();
        KEYWORD_CATEGORY.values().forEach(cat -> byCategory.put(cat, new ArrayList<>()));
        byCategory.put("Autres", new ArrayList<>());

        for (Map.Entry<String, List<QuantityUnit>> entry : ingredientQuantities.entrySet()) {
            String name = entry.getKey();
            List<QuantityUnit> quantities = entry.getValue();

            String aggregated = normalizationService.aggregate(quantities);
            boolean incompatible = aggregated == null && quantities.size() > 1;

            if (incompatible) {
                warnings.add("Unités incompatibles pour '" + name + "' — conservé en lignes séparées");
                for (QuantityUnit qu : quantities) {
                    String cat = categorize(name);
                    byCategory.get(cat).add(new ShoppingItem(name, normalizationService.format(qu), false));
                }
            } else {
                String cat = categorize(name);
                String displayQty = aggregated != null ? aggregated
                        : (quantities.size() == 1 ? normalizationService.format(quantities.get(0)) : "");
                byCategory.get(cat).add(new ShoppingItem(name, displayQty, false));
            }
        }

        List<ShoppingCategory> categories = byCategory.entrySet().stream()
                .filter(e -> !e.getValue().isEmpty())
                .map(e -> new ShoppingCategory(e.getKey(), e.getValue()))
                .toList();

        return new ShoppingListResult(weekStart, categories, warnings);
    }

    private boolean isBasic(String name) {
        return aiProperties.shoppingList().basicNecessities().stream()
                .anyMatch(basic -> name.contains(basic.toLowerCase()));
    }

    private QuantityUnit parseDescription(String description) {
        if (description == null || description.isBlank()) {
            return new QuantityUnit(1, "unité");
        }
        Matcher m = QUANTITY_PATTERN.matcher(description.trim());
        if (m.find()) {
            try {
                double qty = Double.parseDouble(m.group(1).replace(",", "."));
                String unit = m.group(2) != null ? m.group(2).trim() : "unité";
                return new QuantityUnit(qty, unit);
            } catch (NumberFormatException ignored) {}
        }
        return new QuantityUnit(1, "unité");
    }

    private String categorize(String ingredientName) {
        String lower = ingredientName.toLowerCase();
        for (Map.Entry<String, String> entry : KEYWORD_CATEGORY.entrySet()) {
            if (lower.matches(".*(" + entry.getKey() + ").*")) {
                return entry.getValue();
            }
        }
        return "Autres";
    }

    private LocalDate parseMonday(String weekStart) {
        try {
            LocalDate date = LocalDate.parse(weekStart, DateTimeFormatter.ISO_LOCAL_DATE);
            if (date.getDayOfWeek() != DayOfWeek.MONDAY) {
                throw new IllegalArgumentException("weekStart doit être un lundi, reçu : " + weekStart);
            }
            return date;
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Format de date invalide, attendu YYYY-MM-DD, reçu : " + weekStart);
        }
    }
}
