package fr.seblaporte.kitchenvault.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "recipe")
@Getter
@Setter
@NoArgsConstructor
public class Recipe {

    @Id
    @Column(name = "id", nullable = false)
    private String id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "difficulty")
    private String difficulty;

    @Column(name = "thumbnail_url", length = 1000)
    private String thumbnailUrl;

    @Column(name = "image_url", length = 1000)
    private String imageUrl;

    @Column(name = "url", length = 1000)
    private String url;

    @Column(name = "serving_size")
    private Integer servingSize;

    @Column(name = "active_time_minutes")
    private Integer activeTimeMinutes;

    @Column(name = "total_time_minutes")
    private Integer totalTimeMinutes;

    @Column(name = "last_synced_at", nullable = false)
    private Instant lastSyncedAt;

    @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private Set<IngredientGroup> ingredientGroups = new HashSet<>();

    @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<NutritionGroup> nutritionGroups = new ArrayList<>();

    @ManyToMany(cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
            name = "recipe_category",
            joinColumns = @JoinColumn(name = "recipe_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id")
    )
    private Set<Category> categories = new HashSet<>();

    @ElementCollection
    @CollectionTable(name = "recipe_note", joinColumns = @JoinColumn(name = "recipe_id"))
    @Column(name = "note", length = 2000)
    @OrderColumn(name = "sort_order")
    private List<String> notes = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "recipe_utensil", joinColumns = @JoinColumn(name = "recipe_id"))
    @Column(name = "utensil")
    @OrderColumn(name = "sort_order")
    private List<String> utensils = new ArrayList<>();

    public Recipe(String id) {
        this.id = id;
    }
}
