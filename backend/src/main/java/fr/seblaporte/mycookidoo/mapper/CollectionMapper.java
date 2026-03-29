package fr.seblaporte.mycookidoo.mapper;

import fr.seblaporte.mycookidoo.entity.Chapter;
import fr.seblaporte.mycookidoo.entity.Collection;
import fr.seblaporte.mycookidoo.entity.Recipe;
import fr.seblaporte.mycookidoo.generated.model.ChapterDto;
import fr.seblaporte.mycookidoo.generated.model.CollectionDto;
import fr.seblaporte.mycookidoo.generated.model.CollectionRecipeDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CollectionMapper {

    CollectionDto toDto(Collection collection);

    ChapterDto toChapterDto(Chapter chapter);

    @Mapping(target = "totalTimeMinutes", source = "totalTimeMinutes")
    CollectionRecipeDto toCollectionRecipeDto(Recipe recipe);
}
