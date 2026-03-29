package fr.seblaporte.mycookidoo.controller;

import fr.seblaporte.mycookidoo.generated.api.CollectionsApiDelegate;
import fr.seblaporte.mycookidoo.generated.model.CollectionDto;
import fr.seblaporte.mycookidoo.mapper.CollectionMapper;
import fr.seblaporte.mycookidoo.service.CollectionService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

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
    public ResponseEntity<List<CollectionDto>> listCollections() {
        List<CollectionDto> dtos = collectionService.listCollections().stream()
                .map(collectionMapper::toDto)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @Override
    public ResponseEntity<CollectionDto> getCollectionById(String id) {
        return collectionService.getCollectionById(id)
                .map(collectionMapper::toDto)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
