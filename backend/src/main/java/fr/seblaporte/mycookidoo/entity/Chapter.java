package fr.seblaporte.mycookidoo.entity;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "chapter")
public class Chapter {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "collection_id", nullable = false)
    private Collection collection;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @ManyToMany(cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
            name = "chapter_recipe",
            joinColumns = @JoinColumn(name = "chapter_id"),
            inverseJoinColumns = @JoinColumn(name = "recipe_id")
    )
    @OrderColumn(name = "sort_order")
    private List<Recipe> recipes = new ArrayList<>();

    protected Chapter() {}

    public Chapter(Collection collection, String name, int sortOrder) {
        this.collection = collection;
        this.name = name;
        this.sortOrder = sortOrder;
    }

    public UUID getId() { return id; }
    public Collection getCollection() { return collection; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public List<Recipe> getRecipes() { return recipes; }
    public void setRecipes(List<Recipe> recipes) { this.recipes = recipes; }
}
