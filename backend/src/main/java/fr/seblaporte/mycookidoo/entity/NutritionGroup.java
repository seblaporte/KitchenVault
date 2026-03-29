package fr.seblaporte.mycookidoo.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "nutrition_group")
public class NutritionGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipe_id", nullable = false)
    private Recipe recipe;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "quantity")
    private Integer quantity;

    @Column(name = "unit_notation")
    private String unitNotation;

    @OneToMany(mappedBy = "nutritionGroup", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Nutrition> nutritions = new ArrayList<>();

    protected NutritionGroup() {}

    public NutritionGroup(Recipe recipe, String name, Integer quantity, String unitNotation) {
        this.recipe = recipe;
        this.name = name;
        this.quantity = quantity;
        this.unitNotation = unitNotation;
    }

    public UUID getId() { return id; }
    public Recipe getRecipe() { return recipe; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public String getUnitNotation() { return unitNotation; }
    public void setUnitNotation(String unitNotation) { this.unitNotation = unitNotation; }
    public List<Nutrition> getNutritions() { return nutritions; }
    public void setNutritions(List<Nutrition> nutritions) { this.nutritions = nutritions; }
}
