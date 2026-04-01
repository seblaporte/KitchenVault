package fr.seblaporte.kitchenvault.controller;

import fr.seblaporte.kitchenvault.generated.api.CollectionsApiDelegate;
import fr.seblaporte.kitchenvault.generated.model.CollectionDto;
import fr.seblaporte.kitchenvault.mapper.CollectionMapper;
import fr.seblaporte.kitchenvault.service.CollectionService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class CollectionsDelegate implements CollectionsApiDelegate {

    private final CollectionService collectionService;
    private final CollectionMapper collectionMapper;

    public CollectionsDelegate(CollectionService collectionService, CollectionMapper collectionMapper) {
        this.collectionService = collectionService;
        this.collectionMapper = collectionMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseEntity<List<CollectionDto>> listCollections() {
        List<CollectionDto> dtos = collectionService.listCollections().stream()
                .map(collectionMapper::toDto)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseEntity<CollectionDto> getCollectionById(String id) {
        return collectionService.getCollectionById(id)
                .map(collectionMapper::toDto)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
