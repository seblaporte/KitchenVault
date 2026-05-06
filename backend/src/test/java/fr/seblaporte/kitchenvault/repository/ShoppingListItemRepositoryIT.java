package fr.seblaporte.kitchenvault.repository;

import fr.seblaporte.kitchenvault.entity.ShoppingCategory;
import fr.seblaporte.kitchenvault.entity.ShoppingListItem;
import org.assertj.db.type.Table;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ShoppingListItemRepositoryIT {

    @Autowired ShoppingListItemRepository repository;
    @Autowired DataSource dataSource;

    @BeforeEach
    void cleanUp() {
        repository.deleteAllInBatch();
    }

    @Test
    void save_persistsAllFields_dbReflectsValues() {
        ShoppingListItem item = makeItem("carottes", "300g", ShoppingCategory.PRODUCE, "[\"r-1\"]", false, false);

        repository.save(item);

        org.assertj.db.api.Assertions.assertThat(new Table(dataSource, "shopping_list_item"))
                .hasNumberOfRows(1)
                .row(0)
                    .value("name").isEqualTo("carottes")
                    .value("quantity").isEqualTo("300g")
                    .value("category").isEqualTo("PRODUCE")
                    .value("checked").isEqualTo(false)
                    .value("custom").isEqualTo(false)
                    .value("created_at").isNotNull()
                    .value("updated_at").isNotNull();
    }

    @Test
    void findAllByOrderByCreatedAtAsc_returnsInInsertionOrder() throws InterruptedException {
        ShoppingListItem first = repository.save(makeItem("carottes", "300g", ShoppingCategory.PRODUCE, "[]", false, false));
        Thread.sleep(10);
        ShoppingListItem second = repository.save(makeItem("tomates", "400g", ShoppingCategory.PRODUCE, "[]", false, false));

        List<ShoppingListItem> result = repository.findAllByOrderByCreatedAtAsc();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo(first.getId());
        assertThat(result.get(1).getId()).isEqualTo(second.getId());
    }

    @Test
    void findAllByCheckedTrue_returnsOnlyCheckedItems() {
        repository.save(makeItem("carottes", "300g", ShoppingCategory.PRODUCE, "[]", true, false));
        repository.save(makeItem("tomates", "400g", ShoppingCategory.PRODUCE, "[]", false, false));

        List<ShoppingListItem> result = repository.findAllByCheckedTrueOrderByCreatedAtAsc();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("carottes");
    }

    @Test
    void findBySourceRecipeId_returnsItemsContainingRecipeId() {
        repository.save(makeItem("carottes", "300g", ShoppingCategory.PRODUCE, "[\"r-1\",\"r-2\"]", false, false));
        repository.save(makeItem("tomates", "400g", ShoppingCategory.PRODUCE, "[\"r-3\"]", false, false));

        List<ShoppingListItem> result = repository.findBySourceRecipeId("r-1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("carottes");
    }

    @Test
    void findBySourceRecipeId_unknownRecipeId_returnsEmpty() {
        repository.save(makeItem("carottes", "300g", ShoppingCategory.PRODUCE, "[\"r-1\"]", false, false));

        List<ShoppingListItem> result = repository.findBySourceRecipeId("r-unknown");

        assertThat(result).isEmpty();
    }

    // ─── Helper ───────────────────────────────────────────────────────────────

    private ShoppingListItem makeItem(String name, String quantity, ShoppingCategory category,
                                      String sourceRecipeIds, boolean checked, boolean custom) {
        ShoppingListItem item = new ShoppingListItem(name, quantity, category, sourceRecipeIds);
        item.setChecked(checked);
        item.setCustom(custom);
        return item;
    }
}
