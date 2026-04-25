package fr.seblaporte.kitchenvault.ai.tool;

import fr.seblaporte.kitchenvault.ai.service.SeasonalVegetableService;
import fr.seblaporte.kitchenvault.ai.service.SeasonalVegetableService.SeasonalVegetablesResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeasonalVegetableToolTest {

    @Mock SeasonalVegetableService service;
    @InjectMocks SeasonalVegetableTool tool;

    @Test
    void getSeasonalVegetables_validMonth_delegatesToService() {
        SeasonalVegetablesResult expected = new SeasonalVegetablesResult(5, "May", List.of());
        when(service.getSeasonalVegetables(5)).thenReturn(expected);

        SeasonalVegetablesResult result = tool.getSeasonalVegetables(5);

        assertThat(result).isEqualTo(expected);
        verify(service).getSeasonalVegetables(5);
    }

    @Test
    void getSeasonalVegetables_monthZero_throwsIllegalArgument() {
        assertThatThrownBy(() -> tool.getSeasonalVegetables(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1 et 12");
    }

    @Test
    void getSeasonalVegetables_month13_throwsIllegalArgument() {
        assertThatThrownBy(() -> tool.getSeasonalVegetables(13))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1 et 12");
    }

    @Test
    void getSeasonalVegetables_december_delegatesToService() {
        SeasonalVegetablesResult expected = new SeasonalVegetablesResult(12, "December", List.of());
        when(service.getSeasonalVegetables(12)).thenReturn(expected);

        assertThat(tool.getSeasonalVegetables(12)).isEqualTo(expected);
    }
}
