package fr.seblaporte.mycookidoo.service;

import fr.seblaporte.mycookidoo.entity.Collection;
import fr.seblaporte.mycookidoo.repository.CollectionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class CollectionService {

    private final CollectionRepository collectionRepository;

    public CollectionService(CollectionRepository collectionRepository) {
        this.collectionRepository = collectionRepository;
    }

    public List<Collection> listCollections() {
        return collectionRepository.findAllWithChaptersAndRecipes();
    }

    public Optional<Collection> getCollectionById(String id) {
        return collectionRepository.findByIdWithChaptersAndRecipes(id);
    }
}
