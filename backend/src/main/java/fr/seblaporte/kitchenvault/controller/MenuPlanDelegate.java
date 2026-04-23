package fr.seblaporte.kitchenvault.controller;

import fr.seblaporte.kitchenvault.entity.MealPlanEntry;
import fr.seblaporte.kitchenvault.entity.Recipe;
import fr.seblaporte.kitchenvault.generated.api.MenuPlanApiDelegate;
import fr.seblaporte.kitchenvault.generated.model.DayPlanDto;
import fr.seblaporte.kitchenvault.generated.model.MealPlanEntryDto;
import fr.seblaporte.kitchenvault.generated.model.MealPlanUpsertDto;
import fr.seblaporte.kitchenvault.generated.model.MealType;
import fr.seblaporte.kitchenvault.generated.model.MenuPlanDto;
import fr.seblaporte.kitchenvault.generated.model.RecipeHistoryDto;
import fr.seblaporte.kitchenvault.mapper.MealPlanMapper;
import fr.seblaporte.kitchenvault.service.MealPlanService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class MenuPlanDelegate implements MenuPlanApiDelegate {

    private final MealPlanService mealPlanService;
    private final MealPlanMapper mealPlanMapper;

    public MenuPlanDelegate(MealPlanService mealPlanService, MealPlanMapper mealPlanMapper) {
        this.mealPlanService = mealPlanService;
        this.mealPlanMapper = mealPlanMapper;
    }

    @Override
    public ResponseEntity<MenuPlanDto> getWeekPlan(LocalDate weekStart) {
        if (weekStart.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new IllegalArgumentException("weekStart must be a Monday");
        }

        List<MealPlanEntry> entries = mealPlanService.getWeekPlan(weekStart);
        Map<LocalDate, Map<fr.seblaporte.kitchenvault.entity.MealType, MealPlanEntry>> byDateAndType = entries.stream()
                .collect(Collectors.groupingBy(
                        MealPlanEntry::getEntryDate,
                        Collectors.toMap(MealPlanEntry::getMealType, Function.identity())
                ));

        List<DayPlanDto> days = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate date = weekStart.plusDays(i);
            Map<fr.seblaporte.kitchenvault.entity.MealType, MealPlanEntry> dayEntries =
                    byDateAndType.getOrDefault(date, Map.of());

            DayPlanDto day = new DayPlanDto(date);
            MealPlanEntry lunchEntry = dayEntries.get(fr.seblaporte.kitchenvault.entity.MealType.LUNCH);
            day.setLunch(lunchEntry != null ? mealPlanMapper.toEntryDto(lunchEntry) : null);
            MealPlanEntry dinnerEntry = dayEntries.get(fr.seblaporte.kitchenvault.entity.MealType.DINNER);
            day.setDinner(dinnerEntry != null ? mealPlanMapper.toEntryDto(dinnerEntry) : null);
            days.add(day);
        }

        MenuPlanDto dto = new MenuPlanDto(days);
        return ResponseEntity.ok(dto);
    }

    @Override
    public ResponseEntity<MealPlanEntryDto> upsertEntry(LocalDate date, MealType mealType, MealPlanUpsertDto mealPlanUpsertDto) {
        fr.seblaporte.kitchenvault.entity.MealType entityMealType =
                fr.seblaporte.kitchenvault.entity.MealType.valueOf(mealType.getValue());
        try {
            MealPlanEntry entry = mealPlanService.upsertEntry(date, entityMealType, mealPlanUpsertDto.getRecipeId());
            return ResponseEntity.ok(mealPlanMapper.toEntryDto(entry));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @Override
    public ResponseEntity<Void> removeEntry(LocalDate date, MealType mealType) {
        fr.seblaporte.kitchenvault.entity.MealType entityMealType =
                fr.seblaporte.kitchenvault.entity.MealType.valueOf(mealType.getValue());
        mealPlanService.removeEntry(date, entityMealType);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<List<MealPlanEntryDto>> getSuggestions(LocalDate date, MealType mealType, Integer maxTotalMinutes, Integer count) {
        fr.seblaporte.kitchenvault.entity.MealType entityMealType =
                fr.seblaporte.kitchenvault.entity.MealType.valueOf(mealType.getValue());
        int effectiveCount = count != null ? count : 3;
        List<Recipe> recipes = mealPlanService.suggest(date, entityMealType, maxTotalMinutes, effectiveCount);
        List<MealPlanEntryDto> dtos = recipes.stream().map(mealPlanMapper::toEntryDtoFromRecipe).toList();
        return ResponseEntity.ok(dtos);
    }

    @Override
    public ResponseEntity<RecipeHistoryDto> getRecipeHistory(String recipeId, Integer limit) {
        int effectiveLimit = limit != null ? limit : 52;
        List<MealPlanEntry> entries = mealPlanService.getRecipeHistory(recipeId, effectiveLimit);

        RecipeHistoryDto dto = new RecipeHistoryDto();
        dto.setRecipeId(recipeId);
        dto.setDates(entries.stream().map(MealPlanEntry::getEntryDate).toList());
        return ResponseEntity.ok(dto);
    }
}
