package fr.seblaporte.mycookidoo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "nutrition_group")
@Getter
@Setter
@NoArgsConstructor
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

    public NutritionGroup(Recipe recipe, String name, Integer quantity, String unitNotation) {
        this.recipe = recipe;
        this.name = name;
        this.quantity = quantity;
        this.unitNotation = unitNotation;
    }
}
