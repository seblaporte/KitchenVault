package fr.seblaporte.kitchenvault.controller;

import fr.seblaporte.kitchenvault.entity.RecipeListRole;
import fr.seblaporte.kitchenvault.generated.api.WeeklyReviewApiController;
import fr.seblaporte.kitchenvault.service.WeeklyReviewService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {WeeklyReviewApiController.class})
@Import(WeeklyReviewDelegate.class)
class WeeklyReviewDelegateTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean WeeklyReviewService weeklyReviewService;

    @Test
    void getWeeklyReview_nonMonday_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/weekly-reviews/2026-05-05"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("weekStart must be a Monday"));
    }

    @Test
    void getWeeklyReview_returnsItems() throws Exception {
        when(weeklyReviewService.generateReview(LocalDate.of(2026, 5, 4))).thenReturn(List.of(
                new WeeklyReviewService.Item("r-1", "Recette découverte", null, List.of(LocalDate.of(2026, 5, 4)))));

        mockMvc.perform(get("/api/v1/weekly-reviews/2026-05-04"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].recipeId").value("r-1"))
                .andExpect(jsonPath("$.items[0].recipeName").value("Recette découverte"));
    }

    @Test
    void submitWeeklyReviewVotes_nonMonday_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/weekly-reviews/2026-05-05/votes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"votes\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void submitWeeklyReviewVotes_mapsUpToFavoritesAndDownToRejected() throws Exception {
        when(weeklyReviewService.submitVotes(Map.of(
                "r-1", RecipeListRole.FAVORITES,
                "r-2", RecipeListRole.REJECTED
        ))).thenReturn(new WeeklyReviewService.SubmitResult(
                List.of(new WeeklyReviewService.VoteOutcome("r-1", RecipeListRole.FAVORITES),
                        new WeeklyReviewService.VoteOutcome("r-2", RecipeListRole.REJECTED)),
                List.of()));

        mockMvc.perform(post("/api/v1/weekly-reviews/2026-05-04/votes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"votes\":[{\"recipeId\":\"r-1\",\"vote\":\"UP\"},{\"recipeId\":\"r-2\",\"vote\":\"DOWN\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moved.length()").value(2))
                .andExpect(jsonPath("$.failed.length()").value(0));
    }

    @Test
    void submitWeeklyReviewVotes_partialFailure_returnsFailedItems() throws Exception {
        when(weeklyReviewService.submitVotes(any())).thenReturn(new WeeklyReviewService.SubmitResult(
                List.of(new WeeklyReviewService.VoteOutcome("r-1", RecipeListRole.FAVORITES)),
                List.of(new WeeklyReviewService.VoteFailure("r-2", "Cookidoo indisponible"))));

        mockMvc.perform(post("/api/v1/weekly-reviews/2026-05-04/votes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"votes\":[{\"recipeId\":\"r-1\",\"vote\":\"UP\"},{\"recipeId\":\"r-2\",\"vote\":\"DOWN\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moved.length()").value(1))
                .andExpect(jsonPath("$.failed[0].recipeId").value("r-2"))
                .andExpect(jsonPath("$.failed[0].error").value("Cookidoo indisponible"));
    }
}
