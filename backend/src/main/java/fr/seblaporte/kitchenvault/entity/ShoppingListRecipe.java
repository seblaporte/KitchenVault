package fr.seblaporte.kitchenvault.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.lang.Nullable;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "shopping_list_recipe")
@Getter
@Setter
@NoArgsConstructor
public class ShoppingListRecipe {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shopping_list_id", nullable = false)
    private ShoppingList shoppingList;

    @Nullable
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipe_id")
    private Recipe recipe;

    @Column(name = "recipe_name_snapshot", nullable = false, length = 255)
    private String recipeNameSnapshot;

    @Column(name = "recipe_id_snapshot", nullable = false, length = 255)
    private String recipeIdSnapshot;

    @Column(name = "consolidated", nullable = false)
    private boolean consolidated = false;

    @Column(name = "added_at", nullable = false)
    private Instant addedAt;

    public ShoppingListRecipe(ShoppingList shoppingList, Recipe recipe) {
        this.shoppingList = shoppingList;
        this.recipe = recipe;
        this.recipeNameSnapshot = recipe.getName();
        this.recipeIdSnapshot = recipe.getId();
        this.addedAt = Instant.now();
    }
}
