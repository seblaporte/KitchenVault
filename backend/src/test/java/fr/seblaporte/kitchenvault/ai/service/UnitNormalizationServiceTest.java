package fr.seblaporte.kitchenvault.ai.service;

import fr.seblaporte.kitchenvault.ai.service.UnitNormalizationService.QuantityUnit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UnitNormalizationServiceTest {

    private final UnitNormalizationService service = new UnitNormalizationService();

    @Test
    void aggregate_singleEntry_formatsDirectly() {
        assertThat(service.aggregate(List.of(new QuantityUnit(500, "g")))).isEqualTo("500 g");
    }

    @Test
    void aggregate_massUnits_summedAndFormattedInGrams() {
        List<QuantityUnit> entries = List.of(new QuantityUnit(200, "g"), new QuantityUnit(0.3, "kg"));
        assertThat(service.aggregate(entries)).isEqualTo("500 g");
    }

    @Test
    void aggregate_massUnitsAbove1kg_convertedToKg() {
        List<QuantityUnit> entries = List.of(new QuantityUnit(500, "g"), new QuantityUnit(1, "kg"));
        assertThat(service.aggregate(entries)).isEqualTo("1.5 kg");
    }

    @Test
    void aggregate_volumeUnits_summedAndFormattedInMl() {
        List<QuantityUnit> entries = List.of(new QuantityUnit(50, "ml"), new QuantityUnit(3, "cl"));
        assertThat(service.aggregate(entries)).isEqualTo("80 ml");
    }

    @Test
    void aggregate_volumeAbove100ml_formattedInCl() {
        List<QuantityUnit> entries = List.of(new QuantityUnit(1, "dl"), new QuantityUnit(50, "ml"));
        assertThat(service.aggregate(entries)).isEqualTo("15 cl");
    }

    @Test
    void aggregate_volumeAbove1000ml_formattedInLiters() {
        List<QuantityUnit> entries = List.of(new QuantityUnit(1, "l"), new QuantityUnit(0.5, "l"));
        assertThat(service.aggregate(entries)).isEqualTo("1.5 l");
    }

    @Test
    void aggregate_incompatibleFamilies_returnsNull() {
        List<QuantityUnit> entries = List.of(new QuantityUnit(100, "g"), new QuantityUnit(100, "ml"));
        assertThat(service.aggregate(entries)).isNull();
    }

    @Test
    void aggregate_pieceUnits_returnsNull() {
        List<QuantityUnit> entries = List.of(new QuantityUnit(2, "unité"), new QuantityUnit(3, "unité"));
        assertThat(service.aggregate(entries)).isNull();
    }

    @Test
    void aggregate_nullOrEmpty_returnsNull() {
        assertThat(service.aggregate(null)).isNull();
        assertThat(service.aggregate(List.of())).isNull();
    }

    @Test
    void format_smallVolumeSpoonConvertsToMl() {
        assertThat(service.format(new QuantityUnit(2, "c.à.s"))).isEqualTo("30 ml");
    }

    @Test
    void format_pinchConvertsToMl() {
        assertThat(service.format(new QuantityUnit(1, "pincée"))).isEqualTo("0.5 ml");
    }
}
