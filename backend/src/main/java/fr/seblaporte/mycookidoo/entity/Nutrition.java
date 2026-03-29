package fr.seblaporte.mycookidoo.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "nutrition")
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

    protected Nutrition() {}

    public Nutrition(NutritionGroup nutritionGroup, String type, BigDecimal number, String unitType) {
        this.nutritionGroup = nutritionGroup;
        this.type = type;
        this.number = number;
        this.unitType = unitType;
    }

    public UUID getId() { return id; }
    public NutritionGroup getNutritionGroup() { return nutritionGroup; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public BigDecimal getNumber() { return number; }
    public void setNumber(BigDecimal number) { this.number = number; }
    public String getUnitType() { return unitType; }
    public void setUnitType(String unitType) { this.unitType = unitType; }
}
