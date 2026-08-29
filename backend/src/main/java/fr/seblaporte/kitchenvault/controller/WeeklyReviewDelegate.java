package fr.seblaporte.kitchenvault.controller;

import fr.seblaporte.kitchenvault.entity.RecipeListRole;
import fr.seblaporte.kitchenvault.exception.InvalidWeekStartException;
import fr.seblaporte.kitchenvault.generated.api.WeeklyReviewApiDelegate;
import fr.seblaporte.kitchenvault.generated.model.WeeklyReviewDto;
import fr.seblaporte.kitchenvault.generated.model.WeeklyReviewFailedItemDto;
import fr.seblaporte.kitchenvault.generated.model.WeeklyReviewItemDto;
import fr.seblaporte.kitchenvault.generated.model.WeeklyReviewMovedItemDto;
import fr.seblaporte.kitchenvault.generated.model.WeeklyReviewResultDto;
import fr.seblaporte.kitchenvault.generated.model.WeeklyReviewSubmitDto;
import fr.seblaporte.kitchenvault.generated.model.WeeklyReviewVote;
import fr.seblaporte.kitchenvault.service.WeeklyReviewService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class WeeklyReviewDelegate implements WeeklyReviewApiDelegate {

    private final WeeklyReviewService weeklyReviewService;

    public WeeklyReviewDelegate(WeeklyReviewService weeklyReviewService) {
        this.weeklyReviewService = weeklyReviewService;
    }

    @Override
    public ResponseEntity<WeeklyReviewDto> getWeeklyReview(LocalDate weekStart) {
        requireMonday(weekStart);

        var items = weeklyReviewService.generateReview(weekStart).stream()
                .map(this::toItemDto)
                .toList();
        return ResponseEntity.ok(new WeeklyReviewDto(weekStart, items));
    }

    @Override
    public ResponseEntity<WeeklyReviewResultDto> submitWeeklyReviewVotes(
            LocalDate weekStart, WeeklyReviewSubmitDto weeklyReviewSubmitDto) {
        requireMonday(weekStart);

        Map<String, RecipeListRole> targetRoleByRecipeId = weeklyReviewSubmitDto.getVotes().stream()
                .collect(Collectors.toMap(
                        vote -> vote.getRecipeId(),
                        vote -> vote.getVote() == WeeklyReviewVote.UP
                                ? RecipeListRole.FAVORITES
                                : RecipeListRole.REJECTED,
                        (a, b) -> b));

        WeeklyReviewService.SubmitResult result = weeklyReviewService.submitVotes(targetRoleByRecipeId);

        WeeklyReviewResultDto dto = new WeeklyReviewResultDto(
                result.moved().stream()
                        .map(o -> new WeeklyReviewMovedItemDto(o.recipeId(), toGeneratedRole(o.newRole())))
                        .toList(),
                result.failed().stream()
                        .map(f -> new WeeklyReviewFailedItemDto(f.recipeId(), f.error()))
                        .toList());
        return ResponseEntity.ok(dto);
    }

    private WeeklyReviewItemDto toItemDto(WeeklyReviewService.Item item) {
        WeeklyReviewItemDto dto = new WeeklyReviewItemDto(item.recipeId(), item.recipeName(), item.plannedDates());
        dto.setThumbnailUrl(item.thumbnailUrl());
        return dto;
    }

    private fr.seblaporte.kitchenvault.generated.model.RecipeListRole toGeneratedRole(RecipeListRole role) {
        return fr.seblaporte.kitchenvault.generated.model.RecipeListRole.valueOf(role.name());
    }

    private void requireMonday(LocalDate weekStart) {
        if (weekStart.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new InvalidWeekStartException("weekStart must be a Monday");
        }
    }
}
