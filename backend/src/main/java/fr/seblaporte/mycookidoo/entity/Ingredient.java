package fr.seblaporte.mycookidoo.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "ingredient")
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

    protected Ingredient() {}

    public Ingredient(String id, IngredientGroup ingredientGroup, String name, String description, int sortOrder) {
        this.id = id;
        this.ingredientGroup = ingredientGroup;
        this.name = name;
        this.description = description;
        this.sortOrder = sortOrder;
    }

    public String getId() { return id; }
    public IngredientGroup getIngredientGroup() { return ingredientGroup; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
}
