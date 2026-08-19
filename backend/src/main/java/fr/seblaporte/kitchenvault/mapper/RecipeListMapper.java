package fr.seblaporte.kitchenvault.mapper;

import fr.seblaporte.kitchenvault.entity.Collection;
import fr.seblaporte.kitchenvault.entity.RecipeListSettings;
import fr.seblaporte.kitchenvault.generated.model.RecipeListCollectionRefDto;
import fr.seblaporte.kitchenvault.generated.model.RecipeListMembershipDto;
import fr.seblaporte.kitchenvault.generated.model.RecipeListSettingsDto;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface RecipeListMapper {

    RecipeListSettingsDto toDto(RecipeListSettings settings);

    List<RecipeListSettingsDto> toDtoList(List<RecipeListSettings> settings);

    RecipeListCollectionRefDto toCollectionRefDto(Collection collection);

    fr.seblaporte.kitchenvault.generated.model.RecipeListRole toGeneratedRole(
            fr.seblaporte.kitchenvault.entity.RecipeListRole role);

    default RecipeListMembershipDto toMembershipDto(fr.seblaporte.kitchenvault.entity.RecipeListRole role) {
        RecipeListMembershipDto dto = new RecipeListMembershipDto();
        dto.setRole(toGeneratedRole(role));
        return dto;
    }
}
