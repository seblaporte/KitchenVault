package fr.seblaporte.kitchenvault.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "seasonal_vegetable")
public class SeasonalVegetable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "integer[]", nullable = false)
    @JdbcTypeCode(SqlTypes.ARRAY)
    private int[] months;

    protected SeasonalVegetable() {}

    public SeasonalVegetable(String name, int[] months) {
        this.name = name;
        this.months = months;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public int[] getMonths() { return months; }
    public void setName(String name) { this.name = name; }
    public void setMonths(int[] months) { this.months = months; }
}
