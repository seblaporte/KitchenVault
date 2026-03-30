package fr.seblaporte.mycookidoo.repository;

import fr.seblaporte.mycookidoo.entity.Category;
import fr.seblaporte.mycookidoo.entity.Chapter;
import fr.seblaporte.mycookidoo.entity.Recipe;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

public final class RecipeSpecification {

    private RecipeSpecification() {}

    public static Specification<Recipe> nameContains(String search) {
        return (root, query, cb) ->
                cb.like(cb.lower(root.get("name")), "%" + search.toLowerCase() + "%");
    }

    public static Specification<Recipe> inCategories(List<String> categoryIds) {
        return (root, query, cb) -> {
            Join<Recipe, Category> categories = root.join("categories");
            return categories.get("id").in(categoryIds);
        };
    }

    public static Specification<Recipe> inDifficulties(List<String> difficulties) {
        return (root, query, cb) -> root.get("difficulty").in(difficulties);
    }

    public static Specification<Recipe> maxTotalTime(int maxMinutes) {
        return (root, query, cb) ->
                cb.lessThanOrEqualTo(root.get("totalTimeMinutes"), maxMinutes);
    }

    public static Specification<Recipe> inCollections(List<String> collectionIds) {
        return (root, query, cb) -> {
            Subquery<String> sub = query.subquery(String.class);
            Root<Chapter> chapter = sub.from(Chapter.class);
            Join<Chapter, Recipe> recipes = chapter.join("recipes");
            sub.select(recipes.get("id"))
               .where(chapter.get("collection").get("id").in(collectionIds));
            return root.get("id").in(sub);
        };
    }
}
