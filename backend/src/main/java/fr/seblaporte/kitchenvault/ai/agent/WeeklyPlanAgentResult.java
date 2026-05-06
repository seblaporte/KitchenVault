package fr.seblaporte.kitchenvault.ai.agent;

import dev.langchain4j.model.output.structured.Description;

import java.util.List;

@Description("résultat de la planification hebdomadaire")
public record WeeklyPlanAgentResult(
        @Description("réponse conversationnelle de l'assistant à destination de l'utilisateur, en Markdown")
        String reply,

        @Description("liste des actions rapides contextuelles à proposer à l'utilisateur sous forme de chips. " +
                "Chaque action a un label affiché et un texte à renvoyer comme message. " +
                "Exemples : 'Changer le déjeuner de lundi', 'Je valide le menu ✓', 'Réinitialiser la semaine'. " +
                "Liste vide si aucune action contextuelle pertinente.")
        List<QuickAction> quickActions,

        @Description("recettes à placer dans le planning. " +
                "Remplis pour la génération initiale ET pour les modifications proposées. " +
                "Liste vide si aucune recette à placer (ex : réponse conversationnelle pure, CONFIRM, REJECT).")
        List<MealSlotAssignment> mealAssignments,

        @Description("action à exécuter sur les changements en attente. " +
                "APPLY_PENDING si l'utilisateur confirme les modifications proposées. " +
                "CLEAR_PENDING si l'utilisateur refuse les modifications proposées. " +
                "null sinon.")
        AgentAction action
) {
    @Description("action rapide contextuelle")
    public record QuickAction(
            @Description("texte affiché sur le chip")
            String label,

            @Description("message à renvoyer comme prochain message utilisateur")
            String action
    ) {}

    @Description("recette à placer sur un créneau du planning")
    public record MealSlotAssignment(
            @Description("date au format YYYY-MM-DD")
            String date,

            @Description("LUNCH ou DINNER")
            String mealType,

            @Description("identifiant UUID de la recette dans KitchenVault")
            String recipeId,

            @Description("nom de la recette")
            String recipeName
    ) {}

    public enum AgentAction {
        APPLY_PENDING,
        CLEAR_PENDING
    }
}
