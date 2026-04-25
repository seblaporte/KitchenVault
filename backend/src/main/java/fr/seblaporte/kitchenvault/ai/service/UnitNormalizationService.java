package fr.seblaporte.kitchenvault.ai.service;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class UnitNormalizationService {

    public enum UnitFamily { MASS, VOLUME, SMALL_VOLUME, PIECE, UNKNOWN }

    public record QuantityUnit(double quantity, String unit) {}

    private static final Map<String, Double> TO_GRAMS = Map.of(
            "g", 1.0, "kg", 1000.0, "mg", 0.001
    );

    private static final Map<String, Double> TO_ML = Map.of(
            "ml", 1.0, "cl", 10.0, "dl", 100.0, "l", 1000.0,
            "c.à.s", 15.0, "c.à.c", 5.0, "pincée", 0.5
    );

    private static final Map<String, Double> SMALL_VOLUME_ML = Map.of(
            "c.à.s", 15.0, "c.à.c", 5.0, "pincée", 0.5
    );

    public UnitFamily getFamily(String unit) {
        if (unit == null) return UnitFamily.UNKNOWN;
        String normalized = unit.trim().toLowerCase();
        if (TO_GRAMS.containsKey(normalized)) return UnitFamily.MASS;
        if (SMALL_VOLUME_ML.containsKey(normalized)) return UnitFamily.SMALL_VOLUME;
        if (TO_ML.containsKey(normalized)) return UnitFamily.VOLUME;
        return UnitFamily.PIECE;
    }

    /**
     * Aggregates a list of quantities with potentially mixed units.
     * Returns null if all entries are incompatible (caller keeps separate lines).
     */
    public String aggregate(List<QuantityUnit> entries) {
        if (entries == null || entries.isEmpty()) return null;
        if (entries.size() == 1) {
            return format(entries.get(0));
        }

        UnitFamily family = getFamily(entries.get(0).unit());
        boolean allSameFamily = entries.stream().allMatch(e -> getFamily(e.unit()) == family);

        if (!allSameFamily || family == UnitFamily.UNKNOWN || family == UnitFamily.PIECE) {
            return null;
        }

        double totalBase = entries.stream()
                .mapToDouble(e -> toBase(e.quantity(), e.unit(), family))
                .sum();

        return formatBase(totalBase, family);
    }

    public String format(QuantityUnit qu) {
        UnitFamily family = getFamily(qu.unit());
        double base = toBase(qu.quantity(), qu.unit(), family);
        return formatBase(base, family);
    }

    private double toBase(double qty, String unit, UnitFamily family) {
        String normalized = unit != null ? unit.trim().toLowerCase() : "";
        return switch (family) {
            case MASS -> qty * Objects.requireNonNullElse(TO_GRAMS.get(normalized), 1.0);
            case VOLUME, SMALL_VOLUME -> qty * Objects.requireNonNullElse(TO_ML.get(normalized), 1.0);
            default -> qty;
        };
    }

    private String formatBase(double base, UnitFamily family) {
        return switch (family) {
            case MASS -> {
                if (base >= 1000) yield formatNumber(base / 1000) + " kg";
                yield formatNumber(base) + " g";
            }
            case VOLUME, SMALL_VOLUME -> {
                if (base >= 1000) yield formatNumber(base / 1000) + " l";
                if (base >= 100) yield formatNumber(base / 10) + " cl";
                yield formatNumber(base) + " ml";
            }
            default -> formatNumber(base) + " unité(s)";
        };
    }

    private String formatNumber(double n) {
        if (n == Math.floor(n)) return String.valueOf((long) n);
        return String.format(java.util.Locale.US, "%.1f", n);
    }
}
