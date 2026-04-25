package fr.seblaporte.kitchenvault.ai.service;

import fr.seblaporte.kitchenvault.entity.SeasonalVegetable;
import fr.seblaporte.kitchenvault.repository.SeasonalVegetableRepository;
import org.springframework.stereotype.Service;

import java.time.Month;
import java.util.Arrays;
import java.util.List;

@Service
public class SeasonalVegetableService {

    public record SeasonalVegetableDto(String name, boolean isPeakSeason) {}

    public record SeasonalVegetablesResult(int month, String monthName, List<SeasonalVegetableDto> vegetables) {}

    private final SeasonalVegetableRepository repository;

    public SeasonalVegetableService(SeasonalVegetableRepository repository) {
        this.repository = repository;
    }

    public SeasonalVegetablesResult getSeasonalVegetables(int month) {
        List<SeasonalVegetable> vegetables = repository.findByMonth(month);
        List<SeasonalVegetableDto> dtos = vegetables.stream()
                .map(v -> new SeasonalVegetableDto(v.getName(), isPeakSeason(v.getMonths(), month)))
                .toList();
        String monthName = Month.of(month).name().charAt(0) + Month.of(month).name().substring(1).toLowerCase();
        return new SeasonalVegetablesResult(month, monthName, dtos);
    }

    private boolean isPeakSeason(int[] months, int currentMonth) {
        if (months.length <= 2) {
            return false;
        }
        int first = months[0];
        int last = months[months.length - 1];
        return currentMonth != first && currentMonth != last;
    }
}
