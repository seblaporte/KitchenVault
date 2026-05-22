package fr.seblaporte.kitchenvault.mapper;

import fr.seblaporte.kitchenvault.entity.ShoppingList;
import fr.seblaporte.kitchenvault.entity.ShoppingListItem;
import fr.seblaporte.kitchenvault.entity.ShoppingListRecipe;
import fr.seblaporte.kitchenvault.generated.model.ShoppingCategory;
import fr.seblaporte.kitchenvault.generated.model.ShoppingListDto;
import fr.seblaporte.kitchenvault.generated.model.ShoppingListItemDto;
import fr.seblaporte.kitchenvault.generated.model.ShoppingListRecipeDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ShoppingListMapper {

    @Mapping(target = "recipeId", source = "recipeIdSnapshot")
    @Mapping(target = "recipeName", source = "recipeNameSnapshot")
    @Mapping(target = "recipeIdSnapshot", source = "recipeIdSnapshot")
    @Mapping(target = "addedAt", expression = "java(selection.getAddedAt() != null ? selection.getAddedAt().atOffset(java.time.ZoneOffset.UTC) : null)")
    @Mapping(target = "thumbnailUrl", expression = "java(selection.getRecipe() != null ? selection.getRecipe().getThumbnailUrl() : null)")
    ShoppingListRecipeDto toRecipeDto(ShoppingListRecipe selection);

    @Mapping(target = "createdAt", expression = "java(item.getCreatedAt() != null ? item.getCreatedAt().atOffset(java.time.ZoneOffset.UTC) : null)")
    @Mapping(target = "category", expression = "java(toGeneratedCategory(item.getCategory()))")
    ShoppingListItemDto toItemDto(ShoppingListItem item);

    @Mapping(target = "consolidatedAt", expression = "java(list.getConsolidatedAt() != null ? list.getConsolidatedAt().atOffset(java.time.ZoneOffset.UTC) : null)")
    ShoppingListDto toDto(ShoppingList list);

    default ShoppingCategory toGeneratedCategory(fr.seblaporte.kitchenvault.entity.ShoppingCategory category) {
        if (category == null) return ShoppingCategory.OTHER;
        return ShoppingCategory.fromValue(category.name());
    }
}
