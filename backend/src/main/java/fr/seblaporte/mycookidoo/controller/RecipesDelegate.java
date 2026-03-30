package fr.seblaporte.mycookidoo.controller;

import fr.seblaporte.mycookidoo.generated.api.RecipesApiDelegate;
import fr.seblaporte.mycookidoo.generated.model.RecipeDetailDto;
import fr.seblaporte.mycookidoo.generated.model.RecipePageDto;
import fr.seblaporte.mycookidoo.generated.model.RecipeSummaryDto;
import fr.seblaporte.mycookidoo.mapper.RecipeMapper;
import fr.seblaporte.mycookidoo.service.RecipeService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class RecipesDelegate implements RecipesApiDelegate {

    private final RecipeService recipeService;
    private final RecipeMapper recipeMapper;

    public RecipesDelegate(RecipeService recipeService, RecipeMapper recipeMapper) {
        this.recipeService = recipeService;
        this.recipeMapper = recipeMapper;
    }

    @Override
    public ResponseEntity<RecipePageDto> listRecipes(Integer page, Integer size, String search,
                                                      List<String> categoryIds, List<String> difficulties,
                                                      Integer maxTotalTimeMinutes) {
        int pageNum = page != null ? page : 0;
        int pageSize = size != null ? size : 20;

        Page<fr.seblaporte.mycookidoo.entity.Recipe> resultPage =
                recipeService.listRecipes(search, categoryIds, difficulties, maxTotalTimeMinutes,
                        PageRequest.of(pageNum, pageSize));

        List<RecipeSummaryDto> content = resultPage.getContent().stream()
                .map(recipeMapper::toSummaryDto)
                .toList();

        RecipePageDto dto = new RecipePageDto();
        dto.setContent(content);
        dto.setTotalElements(resultPage.getTotalElements());
        dto.setTotalPages(resultPage.getTotalPages());
        dto.setPage(pageNum);
        dto.setSize(pageSize);

        return ResponseEntity.ok(dto);
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseEntity<RecipeDetailDto> getRecipeById(String id) {
        return recipeService.getRecipeById(id)
                .map(recipeMapper::toDetailDto)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
