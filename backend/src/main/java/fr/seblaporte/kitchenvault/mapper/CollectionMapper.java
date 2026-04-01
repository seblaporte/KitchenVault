package fr.seblaporte.kitchenvault.mapper;

import fr.seblaporte.kitchenvault.entity.Chapter;
import fr.seblaporte.kitchenvault.entity.Collection;
import fr.seblaporte.kitchenvault.entity.Recipe;
import fr.seblaporte.kitchenvault.generated.model.ChapterDto;
import fr.seblaporte.kitchenvault.generated.model.CollectionDto;
import fr.seblaporte.kitchenvault.generated.model.CollectionRecipeDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CollectionMapper {

    CollectionDto toDto(Collection collection);

    ChapterDto toChapterDto(Chapter chapter);

    @Mapping(target = "totalTimeMinutes", source = "totalTimeMinutes")
    CollectionRecipeDto toCollectionRecipeDto(Recipe recipe);
}
