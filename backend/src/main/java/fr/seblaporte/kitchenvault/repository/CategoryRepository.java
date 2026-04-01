package fr.seblaporte.kitchenvault.repository;

import fr.seblaporte.kitchenvault.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, String> {

    List<Category> findAllByOrderByNameAsc();
}
