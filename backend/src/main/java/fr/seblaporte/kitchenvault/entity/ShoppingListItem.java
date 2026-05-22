package fr.seblaporte.kitchenvault.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "shopping_list_item")
@Getter
@Setter
@NoArgsConstructor
public class ShoppingListItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shopping_list_id", nullable = false)
    private ShoppingList shoppingList;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "quantity", length = 255)
    private String quantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    private ShoppingCategory category;

    @Column(name = "checked", nullable = false)
    private boolean checked = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_recipe_ids", columnDefinition = "jsonb", nullable = false)
    private List<String> sourceRecipeIds = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_recipe_names", columnDefinition = "jsonb")
    private Map<String, String> sourceRecipeNames = new HashMap<>();

    @Column(name = "custom", nullable = false)
    private boolean custom = false;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public ShoppingListItem(ShoppingList shoppingList, String name, String quantity,
                            ShoppingCategory category, List<String> sourceRecipeIds,
                            Map<String, String> sourceRecipeNames, int sortOrder) {
        this.shoppingList = shoppingList;
        this.name = name;
        this.quantity = quantity;
        this.category = category;
        this.sourceRecipeIds = sourceRecipeIds != null ? sourceRecipeIds : new ArrayList<>();
        this.sourceRecipeNames = sourceRecipeNames != null ? sourceRecipeNames : new HashMap<>();
        this.sortOrder = sortOrder;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }
}
