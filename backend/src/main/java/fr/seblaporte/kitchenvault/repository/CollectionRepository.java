package fr.seblaporte.kitchenvault.repository;

import fr.seblaporte.kitchenvault.entity.Collection;
import fr.seblaporte.kitchenvault.entity.CollectionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CollectionRepository extends JpaRepository<Collection, String> {

    List<Collection> findByType(CollectionType type);

    @Query("SELECT c FROM Collection c LEFT JOIN FETCH c.chapters WHERE c.id = :id")
    Optional<Collection> findByIdWithChapters(@Param("id") String id);

    @Query("SELECT c FROM Collection c LEFT JOIN FETCH c.chapters")
    List<Collection> findAllWithChapters();
}
