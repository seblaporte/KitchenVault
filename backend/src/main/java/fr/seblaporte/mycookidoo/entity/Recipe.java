package fr.seblaporte.mycookidoo.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "recipe")
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
    private List<IngredientGroup> ingredientGroups = new ArrayList<>();

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

    protected Recipe() {}

    public Recipe(String id) {
        this.id = id;
    }

    public String getId() { return id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }

    public String getThumbnailUrl() { return thumbnailUrl; }
    public void setThumbnailUrl(String thumbnailUrl) { this.thumbnailUrl = thumbnailUrl; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public Integer getServingSize() { return servingSize; }
    public void setServingSize(Integer servingSize) { this.servingSize = servingSize; }

    public Integer getActiveTimeMinutes() { return activeTimeMinutes; }
    public void setActiveTimeMinutes(Integer activeTimeMinutes) { this.activeTimeMinutes = activeTimeMinutes; }

    public Integer getTotalTimeMinutes() { return totalTimeMinutes; }
    public void setTotalTimeMinutes(Integer totalTimeMinutes) { this.totalTimeMinutes = totalTimeMinutes; }

    public Instant getLastSyncedAt() { return lastSyncedAt; }
    public void setLastSyncedAt(Instant lastSyncedAt) { this.lastSyncedAt = lastSyncedAt; }

    public List<IngredientGroup> getIngredientGroups() { return ingredientGroups; }
    public void setIngredientGroups(List<IngredientGroup> ingredientGroups) { this.ingredientGroups = ingredientGroups; }

    public List<NutritionGroup> getNutritionGroups() { return nutritionGroups; }
    public void setNutritionGroups(List<NutritionGroup> nutritionGroups) { this.nutritionGroups = nutritionGroups; }

    public Set<Category> getCategories() { return categories; }
    public void setCategories(Set<Category> categories) { this.categories = categories; }

    public List<String> getNotes() { return notes; }
    public void setNotes(List<String> notes) { this.notes = notes; }

    public List<String> getUtensils() { return utensils; }
    public void setUtensils(List<String> utensils) { this.utensils = utensils; }
}
