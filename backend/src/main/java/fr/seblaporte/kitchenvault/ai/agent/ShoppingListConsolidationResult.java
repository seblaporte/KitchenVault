package fr.seblaporte.kitchenvault.ai.agent;

import dev.langchain4j.model.output.structured.Description;
import fr.seblaporte.kitchenvault.entity.ShoppingCategory;

import java.util.List;

@Description("résultat de la consolidation de la liste de courses")
public record ShoppingListConsolidationResult(
        @Description("liste des articles de la liste de courses consolidée, sans les produits de base exclus")
        List<ConsolidatedItem> items
) {
    @Description("article consolidé de la liste de courses")
    public record ConsolidatedItem(
            @Description("nom de l'article en français")
            String name,

            @Description("quantité avec unité, ex: '500g', '2 pièces', '1 litre'. Null si quantité inconnue.")
            String quantity,

            @Description("catégorie parmi: PRODUCE (fruits/légumes), MEAT (viandes/poissons), DAIRY (produits laitiers/oeufs), BAKERY (boulangerie), GROCERY (épicerie sèche), FROZEN (surgelés), SPICES (épices/condiments), OTHER")
            ShoppingCategory category,

            @Description("liste des identifiants (recipeId) des recettes dont provient cet article, tels qu'indiqués entre crochets [ID: xxx] dans le prompt. Un seul élément si l'article vient d'une seule recette, plusieurs si fusionné.")
            List<String> sourceRecipeIds
    ) {}
}
