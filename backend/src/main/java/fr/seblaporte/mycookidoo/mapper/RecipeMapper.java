package fr.seblaporte.mycookidoo.mapper;

import fr.seblaporte.mycookidoo.entity.*;
import fr.seblaporte.mycookidoo.generated.model.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Mapper(componentModel = "spring")
public interface RecipeMapper {

    @Mapping(target = "thumbnailUrl", source = "thumbnailUrl")
    @Mapping(target = "totalTimeMinutes", source = "totalTimeMinutes")
    @Mapping(target = "activeTimeMinutes", source = "activeTimeMinutes")
    RecipeSummaryDto toSummaryDto(Recipe recipe);

    @Mapping(target = "ingredientGroups", source = "ingredientGroups")
    @Mapping(target = "lastSyncedAt", expression = "java(toOffsetDateTime(recipe.getLastSyncedAt()))")
    RecipeDetailDto toDetailDto(Recipe recipe);

    @Mapping(target = "ingredients", source = "ingredients")
    IngredientGroupDto toIngredientGroupDto(IngredientGroup group);

    IngredientDto toIngredientDto(Ingredient ingredient);

    CategoryDto toCategoryDto(Category category);

    @Mapping(target = "nutritions", expression = "java(flattenNutritions(group))")
    NutritionGroupDto toNutritionGroupDto(NutritionGroup group);

    default List<NutritionDto> flattenNutritions(NutritionGroup group) {
        return group.getNutritions().stream()
                .map(n -> {
                    NutritionDto dto = new NutritionDto();
                    dto.setType(n.getType());
                    dto.setNumber(n.getNumber().doubleValue());
                    dto.setUnitType(n.getUnitType());
                    return dto;
                })
                .toList();
    }

    default OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}
