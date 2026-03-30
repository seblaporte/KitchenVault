package fr.seblaporte.mycookidoo.service;

import fr.seblaporte.mycookidoo.entity.Category;
import fr.seblaporte.mycookidoo.repository.CategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    public List<Category> listCategories() {
        return categoryRepository.findAllByOrderByNameAsc();
    }
}
