package fr.seblaporte.kitchenvault.ai.agent;

import dev.langchain4j.model.output.structured.Description;

import java.util.List;

@Description("résultat de la consolidation de la liste de courses")
public record ShoppingListConsolidationResult(

        @Description("liste complète et consolidée des articles. " +
                "Ne contient pas les produits de base (eau, sel, sucre, huile, poivre, etc.). " +
                "Contient tous les articles existants non-basiques fusionnés avec les nouveaux ingrédients de la recette.")
        List<ConsolidatedItem> items

) {
    @Description("un article de la liste de courses consolidée")
    public record ConsolidatedItem(

            @Description("nom normalisé de l'ingrédient, au singulier, en minuscules")
            String name,

            @Description("quantité consolidée (ex: '300g', '2 pièces', '1.5 kg', '200ml'). " +
                    "Null si la quantité est inconnue ou non applicable.")
            String quantity,

            @Description("catégorie de rayon parmi : produce (Fruits & Légumes), meat (Viandes & Poissons), " +
                    "dairy (Crémerie & Œufs), bakery (Boulangerie), grocery (Épicerie), " +
                    "frozen (Surgelés), spices (Épices), other (Autres)")
            String category
    ) {}
}
