package fr.seblaporte.kitchenvault.controller;

import fr.seblaporte.kitchenvault.entity.MealPlanEntry;
import fr.seblaporte.kitchenvault.entity.Recipe;
import fr.seblaporte.kitchenvault.exception.InvalidWeekStartException;
import fr.seblaporte.kitchenvault.generated.api.MenuPlanApiDelegate;
import fr.seblaporte.kitchenvault.generated.model.DayPlanDto;
import fr.seblaporte.kitchenvault.generated.model.MealPlanBulkRequest;
import fr.seblaporte.kitchenvault.generated.model.MealPlanEntryDto;
import fr.seblaporte.kitchenvault.generated.model.MealPlanRelocateDto;
import fr.seblaporte.kitchenvault.generated.model.MealPlanUpsertDto;
import fr.seblaporte.kitchenvault.generated.model.MealType;
import fr.seblaporte.kitchenvault.generated.model.MenuPlanDto;
import fr.seblaporte.kitchenvault.generated.model.RecipeHistoryDto;
import fr.seblaporte.kitchenvault.mapper.MealPlanMapper;
import fr.seblaporte.kitchenvault.service.CookidooCalendarPullService;
import fr.seblaporte.kitchenvault.service.CookidooCalendarSyncService;
import fr.seblaporte.kitchenvault.service.MealPlanService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Component
public class MenuPlanDelegate implements MenuPlanApiDelegate {

    private final MealPlanService mealPlanService;
    private final MealPlanMapper mealPlanMapper;
    private final CookidooCalendarSyncService cookidooCalendarSyncService;
    private final CookidooCalendarPullService cookidooCalendarPullService;

    public MenuPlanDelegate(MealPlanService mealPlanService, MealPlanMapper mealPlanMapper,
                            CookidooCalendarSyncService cookidooCalendarSyncService,
                            CookidooCalendarPullService cookidooCalendarPullService) {
        this.mealPlanService = mealPlanService;
        this.mealPlanMapper = mealPlanMapper;
        this.cookidooCalendarSyncService = cookidooCalendarSyncService;
        this.cookidooCalendarPullService = cookidooCalendarPullService;
    }

    @Override
    public ResponseEntity<MenuPlanDto> getWeekPlan(LocalDate weekStart) {
        if (weekStart.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new InvalidWeekStartException("weekStart must be a Monday");
        }

        List<MealPlanEntry> entries = mealPlanService.getWeekPlan(weekStart);
        Map<LocalDate, Map<fr.seblaporte.kitchenvault.entity.MealType, List<MealPlanEntry>>> byDateAndType = entries.stream()
                .collect(Collectors.groupingBy(
                        MealPlanEntry::getEntryDate,
                        Collectors.groupingBy(MealPlanEntry::getMealType)
                ));

        List<DayPlanDto> days = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate date = weekStart.plusDays(i);
            Map<fr.seblaporte.kitchenvault.entity.MealType, List<MealPlanEntry>> dayEntries =
                    byDateAndType.getOrDefault(date, Map.of());

            List<MealPlanEntryDto> undefinedMeals = dayEntries
                    .getOrDefault(fr.seblaporte.kitchenvault.entity.MealType.UNDEFINED, List.of())
                    .stream()
                    .map(mealPlanMapper::toEntryDto)
                    .toList();

            DayPlanDto day = new DayPlanDto(date, undefinedMeals);
            for (fr.seblaporte.kitchenvault.entity.MealType mt :
                    List.of(fr.seblaporte.kitchenvault.entity.MealType.LUNCH, fr.seblaporte.kitchenvault.entity.MealType.DINNER)) {
                List<MealPlanEntry> slot = dayEntries.get(mt);
                MealPlanEntryDto dto = slot != null && !slot.isEmpty() ? mealPlanMapper.toEntryDto(slot.get(0)) : null;
                switch (mt) {
                    case LUNCH -> day.setLunch(dto);
                    case DINNER -> day.setDinner(dto);
                    default -> throw new IllegalStateException("Unexpected meal type: " + mt);
                }
            }
            days.add(day);
        }

        MenuPlanDto dto = new MenuPlanDto(days);
        return ResponseEntity.ok(dto);
    }

    @Override
    public ResponseEntity<MealPlanEntryDto> addUndefinedEntry(LocalDate date, MealPlanUpsertDto mealPlanUpsertDto) {
        try {
            MealPlanEntry entry = mealPlanService.addUndefinedEntry(date, mealPlanUpsertDto.getRecipeId());
            return ResponseEntity.ok(mealPlanMapper.toEntryDto(entry));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
        // MealPlanService.SlotOccupiedException (recette déjà planifiée ce jour-là) propage vers
        // GlobalExceptionHandler, qui répond 409.
    }

    @Override
    public ResponseEntity<Void> removeUndefinedEntry(Long id) {
        mealPlanService.removeUndefinedEntryById(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<MealPlanEntryDto> relocateUndefinedEntry(Long id, MealPlanRelocateDto mealPlanRelocateDto) {
        try {
            MealPlanEntry entry = mealPlanService.relocateUndefinedEntry(
                    id, mealPlanRelocateDto.getDate(), toEntityMealType(mealPlanRelocateDto.getMealType()));
            return ResponseEntity.ok(mealPlanMapper.toEntryDto(entry));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @Override
    public ResponseEntity<Void> pullWeekFromCookidoo(LocalDate weekStart) {
        if (weekStart.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new InvalidWeekStartException("weekStart must be a Monday");
        }
        cookidooCalendarPullService.pullWeek(weekStart);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<MealPlanEntryDto> upsertEntry(LocalDate date, MealType mealType, MealPlanUpsertDto mealPlanUpsertDto) {
        try {
            MealPlanEntry entry = mealPlanService.upsertEntry(date, toEntityMealType(mealType), mealPlanUpsertDto.getRecipeId());
            return ResponseEntity.ok(mealPlanMapper.toEntryDto(entry));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @Override
    public ResponseEntity<Void> removeEntry(LocalDate date, MealType mealType) {
        mealPlanService.removeEntry(date, toEntityMealType(mealType));
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<MealPlanEntryDto> relocateEntry(LocalDate date, MealType mealType, MealPlanRelocateDto mealPlanRelocateDto) {
        try {
            MealPlanEntry entry = mealPlanService.relocateEntry(
                    date, toEntityMealType(mealType),
                    mealPlanRelocateDto.getDate(), toEntityMealType(mealPlanRelocateDto.getMealType()));
            return ResponseEntity.ok(mealPlanMapper.toEntryDto(entry));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @Override
    public ResponseEntity<List<MealPlanEntryDto>> getSuggestions(LocalDate date, MealType mealType, Integer maxTotalMinutes, Integer count) {
        int effectiveCount = count != null ? count : 3;
        List<Recipe> recipes = mealPlanService.suggest(date, toEntityMealType(mealType), maxTotalMinutes, effectiveCount);
        List<MealPlanEntryDto> dtos = recipes.stream().map(mealPlanMapper::toEntryDtoFromRecipe).toList();
        return ResponseEntity.ok(dtos);
    }

    @Override
    public ResponseEntity<List<MealPlanEntryDto>> upsertBulkEntries(MealPlanBulkRequest request) {
        try {
            List<MealPlanEntry> entries = mealPlanService.upsertBulk(request.getEntries());
            List<MealPlanEntryDto> dtos = entries.stream().map(mealPlanMapper::toEntryDto).toList();
            return ResponseEntity.ok(dtos);
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
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

    @Override
    public ResponseEntity<Void> syncWeekToCookidoo(LocalDate weekStart, Boolean replace) {
        if (weekStart.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new InvalidWeekStartException("weekStart must be a Monday");
        }
        cookidooCalendarSyncService.syncWeek(weekStart, Boolean.TRUE.equals(replace));
        return ResponseEntity.noContent().build();
    }

    private fr.seblaporte.kitchenvault.entity.MealType toEntityMealType(MealType mealType) {
        return fr.seblaporte.kitchenvault.entity.MealType.valueOf(mealType.getValue());
    }
}
