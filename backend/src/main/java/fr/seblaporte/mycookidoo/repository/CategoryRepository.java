package fr.seblaporte.mycookidoo.repository;

import fr.seblaporte.mycookidoo.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, String> {
}
