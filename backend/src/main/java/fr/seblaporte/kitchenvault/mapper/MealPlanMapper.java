package fr.seblaporte.kitchenvault.mapper;

import fr.seblaporte.kitchenvault.entity.MealPlanEntry;
import fr.seblaporte.kitchenvault.entity.Recipe;
import fr.seblaporte.kitchenvault.generated.model.MealPlanEntryDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface MealPlanMapper {

    @Mapping(target = "recipeId", expression = "java(entry.getRecipe() != null ? entry.getRecipe().getId() : null)")
    @Mapping(target = "recipeName", source = "recipeNameSnapshot")
    @Mapping(target = "recipeThumbnailUrl", expression = "java(entry.getRecipe() != null ? entry.getRecipe().getThumbnailUrl() : null)")
    @Mapping(target = "recipeTotalTimeMinutes", expression = "java(entry.getRecipe() != null ? entry.getRecipe().getTotalTimeMinutes() : null)")
    MealPlanEntryDto toEntryDto(MealPlanEntry entry);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "recipeId", source = "id")
    @Mapping(target = "recipeName", source = "name")
    @Mapping(target = "recipeThumbnailUrl", source = "thumbnailUrl")
    @Mapping(target = "recipeTotalTimeMinutes", source = "totalTimeMinutes")
    MealPlanEntryDto toEntryDtoFromRecipe(Recipe recipe);
}
