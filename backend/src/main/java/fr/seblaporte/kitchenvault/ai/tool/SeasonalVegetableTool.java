package fr.seblaporte.kitchenvault.ai.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import fr.seblaporte.kitchenvault.ai.service.SeasonalVegetableService;
import fr.seblaporte.kitchenvault.ai.service.SeasonalVegetableService.SeasonalVegetablesResult;
import org.springframework.stereotype.Component;

@Component
public class SeasonalVegetableTool {

    private final SeasonalVegetableService service;

    public SeasonalVegetableTool(SeasonalVegetableService service) {
        this.service = service;
    }

    @Tool("""
            Retourne la liste des légumes de saison pour un mois donné.
            Appeler cet outil en premier quand l'utilisateur mentionne 'saison', 'saisonnier' ou 'de saison',
            puis enrichir la requête de RecipeSuggestionTool avec les légumes obtenus.
            """)
    public SeasonalVegetablesResult getSeasonalVegetables(
            @P("Numéro du mois (1-12)") int month) {
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("Le mois doit être compris entre 1 et 12, reçu : " + month);
        }
        return service.getSeasonalVegetables(month);
    }
}
