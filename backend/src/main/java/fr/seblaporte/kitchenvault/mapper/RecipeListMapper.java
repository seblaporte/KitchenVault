package fr.seblaporte.kitchenvault.mapper;

import fr.seblaporte.kitchenvault.entity.Collection;
import fr.seblaporte.kitchenvault.entity.Recipe;
import fr.seblaporte.kitchenvault.entity.RecipeListSettings;
import fr.seblaporte.kitchenvault.generated.model.RecipeListCollectionRefDto;
import fr.seblaporte.kitchenvault.generated.model.RecipeListMembershipDto;
import fr.seblaporte.kitchenvault.generated.model.RecipeListOverviewDto;
import fr.seblaporte.kitchenvault.generated.model.RecipeListSettingsDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", uses = RecipeMapper.class)
public interface RecipeListMapper {

    RecipeListSettingsDto toDto(RecipeListSettings settings);

    List<RecipeListSettingsDto> toDtoList(List<RecipeListSettings> settings);

    RecipeListCollectionRefDto toCollectionRefDto(Collection collection);

    @Mapping(target = "role", source = "settings.role")
    @Mapping(target = "displayLabel", source = "settings.displayLabel")
    @Mapping(target = "recipes", source = "recipes")
    RecipeListOverviewDto toOverviewDto(RecipeListSettings settings, List<Recipe> recipes);

    fr.seblaporte.kitchenvault.generated.model.RecipeListRole toGeneratedRole(
            fr.seblaporte.kitchenvault.entity.RecipeListRole role);

    default RecipeListMembershipDto toMembershipDto(fr.seblaporte.kitchenvault.entity.RecipeListRole role) {
        RecipeListMembershipDto dto = new RecipeListMembershipDto();
        dto.setRole(toGeneratedRole(role));
        return dto;
    }
}
