package fr.seblaporte.kitchenvault.ai.service;

import fr.seblaporte.kitchenvault.ai.service.SeasonalVegetableService.SeasonalVegetableDto;
import fr.seblaporte.kitchenvault.ai.service.SeasonalVegetableService.SeasonalVegetablesResult;
import fr.seblaporte.kitchenvault.entity.SeasonalVegetable;
import fr.seblaporte.kitchenvault.repository.SeasonalVegetableRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeasonalVegetableServiceTest {

    @Mock SeasonalVegetableRepository repository;
    @InjectMocks SeasonalVegetableService service;

    @Test
    void getSeasonalVegetables_peakMonthInMiddle_isPeakSeasonTrue() {
        SeasonalVegetable tomate = vegetable("Tomate", new int[]{7, 8, 9});
        when(repository.findByMonth(8)).thenReturn(List.of(tomate));

        SeasonalVegetablesResult result = service.getSeasonalVegetables(8);

        assertThat(result.month()).isEqualTo(8);
        assertThat(result.monthName()).isEqualTo("August");
        assertThat(result.vegetables()).hasSize(1);
        assertThat(result.vegetables().get(0).isPeakSeason()).isTrue();
    }

    @Test
    void getSeasonalVegetables_firstMonth_isPeakSeasonFalse() {
        SeasonalVegetable tomate = vegetable("Tomate", new int[]{7, 8, 9});
        when(repository.findByMonth(7)).thenReturn(List.of(tomate));

        SeasonalVegetablesResult result = service.getSeasonalVegetables(7);

        assertThat(result.vegetables().get(0).isPeakSeason()).isFalse();
    }

    @Test
    void getSeasonalVegetables_lastMonth_isPeakSeasonFalse() {
        SeasonalVegetable tomate = vegetable("Tomate", new int[]{7, 8, 9});
        when(repository.findByMonth(9)).thenReturn(List.of(tomate));

        assertThat(service.getSeasonalVegetables(9).vegetables().get(0).isPeakSeason()).isFalse();
    }

    @Test
    void getSeasonalVegetables_twoMonthsArray_isPeakSeasonFalse() {
        SeasonalVegetable v = vegetable("Asperge", new int[]{4, 5});
        when(repository.findByMonth(4)).thenReturn(List.of(v));

        assertThat(service.getSeasonalVegetables(4).vegetables().get(0).isPeakSeason()).isFalse();
    }

    @Test
    void getSeasonalVegetables_noVegetables_returnsEmptyList() {
        when(repository.findByMonth(1)).thenReturn(List.of());

        SeasonalVegetablesResult result = service.getSeasonalVegetables(1);
        assertThat(result.vegetables()).isEmpty();
        assertThat(result.monthName()).isEqualTo("January");
    }

    private SeasonalVegetable vegetable(String name, int[] months) {
        return new SeasonalVegetable(name, months);
    }
}
