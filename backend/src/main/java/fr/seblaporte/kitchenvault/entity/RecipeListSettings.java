package fr.seblaporte.kitchenvault.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "recipe_list_settings")
@Getter
@Setter
@NoArgsConstructor
public class RecipeListSettings {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private RecipeListRole role;

    @ManyToOne
    @JoinColumn(name = "collection_id")
    private Collection collection;

    @Column(name = "display_label", nullable = false)
    private String displayLabel;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public RecipeListSettings(RecipeListRole role) {
        this.role = role;
    }
}
