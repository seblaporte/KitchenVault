package fr.seblaporte.mycookidoo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "ingredient")
@Getter
@Setter
@NoArgsConstructor
public class Ingredient {

    @Id
    @Column(name = "id", nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ingredient_group_id", nullable = false)
    private IngredientGroup ingredientGroup;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public Ingredient(String id, IngredientGroup ingredientGroup, String name, String description, int sortOrder) {
        this.id = id;
        this.ingredientGroup = ingredientGroup;
        this.name = name;
        this.description = description;
        this.sortOrder = sortOrder;
    }
}
