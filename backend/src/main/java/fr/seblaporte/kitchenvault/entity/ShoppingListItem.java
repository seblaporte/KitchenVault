package fr.seblaporte.kitchenvault.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.lang.Nullable;

import java.time.Instant;
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

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Nullable
    @Column(name = "quantity", length = 255)
    private String quantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    private ShoppingCategory category;

    @Column(name = "checked", nullable = false)
    private boolean checked = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_recipe_ids", nullable = false, columnDefinition = "jsonb")
    private String sourceRecipeIds = "[]";

    @Column(name = "custom", nullable = false)
    private boolean custom = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onPrePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onPreUpdate() {
        this.updatedAt = Instant.now();
    }

    public ShoppingListItem(String name, String quantity, ShoppingCategory category, String sourceRecipeIds) {
        this.name = name;
        this.quantity = quantity;
        this.category = category;
        this.sourceRecipeIds = sourceRecipeIds;
        this.custom = false;
    }

    public static ShoppingListItem customItem(String name, String quantity) {
        ShoppingListItem item = new ShoppingListItem();
        item.name = name;
        item.quantity = quantity;
        item.category = ShoppingCategory.OTHER;
        item.sourceRecipeIds = "[]";
        item.custom = true;
        return item;
    }
}
