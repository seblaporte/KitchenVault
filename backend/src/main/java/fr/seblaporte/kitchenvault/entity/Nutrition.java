package fr.seblaporte.kitchenvault.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "nutrition")
@Getter
@Setter
@NoArgsConstructor
public class Nutrition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "nutrition_group_id", nullable = false)
    private NutritionGroup nutritionGroup;

    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "number", nullable = false, precision = 10, scale = 3)
    private BigDecimal number;

    @Column(name = "unit_type", nullable = false)
    private String unitType;

    public Nutrition(NutritionGroup nutritionGroup, String type, BigDecimal number, String unitType) {
        this.nutritionGroup = nutritionGroup;
        this.type = type;
        this.number = number;
        this.unitType = unitType;
    }
}
