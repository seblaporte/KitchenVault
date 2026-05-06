package fr.seblaporte.kitchenvault.ai.memory;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import javax.sql.DataSource;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PostgresChatMemoryStoreTest {

    private static DataSource dataSource;
    private PostgresChatMemoryStore store;

    @BeforeAll
    static void setUpDatabase() {
        dataSource = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .addScript("test-chat-schema.sql")
                .build();
    }

    @BeforeEach
    void setUp() {
        store = new PostgresChatMemoryStore(dataSource);
        new JdbcTemplate(dataSource).update("DELETE FROM chat_message");
    }

    @Test
    void roundTrip_userMessage() {
        store.updateMessages("s1", List.of(UserMessage.from("Bonjour, suggère-moi une recette.")));

        List<ChatMessage> retrieved = store.getMessages("s1");

        assertThat(retrieved).hasSize(1);
        assertThat(retrieved.get(0)).isInstanceOf(UserMessage.class);
        assertThat(((UserMessage) retrieved.get(0)).singleText())
                .isEqualTo("Bonjour, suggère-moi une recette.");
    }

    @Test
    void roundTrip_aiMessage_textOnly() {
        store.updateMessages("s1", List.of(AiMessage.from("Je te suggère une tarte aux pommes.")));

        List<ChatMessage> retrieved = store.getMessages("s1");

        assertThat(retrieved).hasSize(1);
        AiMessage msg = (AiMessage) retrieved.get(0);
        assertThat(msg.text()).isEqualTo("Je te suggère une tarte aux pommes.");
        assertThat(msg.hasToolExecutionRequests()).isFalse();
    }

    @Test
    void roundTrip_aiMessage_withToolRequests() {
        ToolExecutionRequest toolReq = ToolExecutionRequest.builder()
                .id("tool-1")
                .name("suggestRecipes")
                .arguments("{\"query\":\"recette rapide\"}")
                .build();
        store.updateMessages("s1", List.of(AiMessage.from(List.of(toolReq))));

        List<ChatMessage> retrieved = store.getMessages("s1");

        assertThat(retrieved).hasSize(1);
        AiMessage msg = (AiMessage) retrieved.get(0);
        assertThat(msg.hasToolExecutionRequests()).isTrue();
        assertThat(msg.toolExecutionRequests()).hasSize(1);
        assertThat(msg.toolExecutionRequests().get(0).id()).isEqualTo("tool-1");
        assertThat(msg.toolExecutionRequests().get(0).name()).isEqualTo("suggestRecipes");
        assertThat(msg.toolExecutionRequests().get(0).arguments()).isEqualTo("{\"query\":\"recette rapide\"}");
    }

    @Test
    void roundTrip_toolExecutionResultMessage() {
        store.updateMessages("s1", List.of(
                ToolExecutionResultMessage.from("tool-1", "suggestRecipes", "Tarte aux pommes")));

        List<ChatMessage> retrieved = store.getMessages("s1");

        assertThat(retrieved).hasSize(1);
        ToolExecutionResultMessage msg = (ToolExecutionResultMessage) retrieved.get(0);
        assertThat(msg.id()).isEqualTo("tool-1");
        assertThat(msg.toolName()).isEqualTo("suggestRecipes");
        assertThat(msg.text()).isEqualTo("Tarte aux pommes");
    }

    @Test
    void roundTrip_systemMessage() {
        store.updateMessages("s1", List.of(SystemMessage.from("Tu es un assistant culinaire.")));

        List<ChatMessage> retrieved = store.getMessages("s1");

        assertThat(retrieved).hasSize(1);
        assertThat(((SystemMessage) retrieved.get(0)).text()).isEqualTo("Tu es un assistant culinaire.");
    }

    @Test
    void roundTrip_multipleMessages_preservesOrder() {
        List<ChatMessage> messages = List.of(
                UserMessage.from("Message 1"),
                AiMessage.from("Réponse 1"),
                UserMessage.from("Message 2")
        );

        store.updateMessages("s1", messages);
        List<ChatMessage> retrieved = store.getMessages("s1");

        assertThat(retrieved).hasSize(3);
        assertThat(retrieved.get(0)).isInstanceOf(UserMessage.class);
        assertThat(retrieved.get(1)).isInstanceOf(AiMessage.class);
        assertThat(retrieved.get(2)).isInstanceOf(UserMessage.class);
        assertThat(((UserMessage) retrieved.get(2)).singleText()).isEqualTo("Message 2");
    }

    @Test
    void updateMessages_replacesExistingMessages() {
        store.updateMessages("s1", List.of(UserMessage.from("Premier message")));
        store.updateMessages("s1", List.of(UserMessage.from("Deuxième message")));

        List<ChatMessage> retrieved = store.getMessages("s1");

        assertThat(retrieved).hasSize(1);
        assertThat(((UserMessage) retrieved.get(0)).singleText()).isEqualTo("Deuxième message");
    }

    @Test
    void getMessages_unknownSession_returnsEmptyList() {
        assertThat(store.getMessages("unknown-session")).isEmpty();
    }

    @Test
    void deleteMessages_removesOnlyTargetSession() {
        store.updateMessages("s1", List.of(UserMessage.from("Session 1")));
        store.updateMessages("s2", List.of(UserMessage.from("Session 2")));

        store.deleteMessages("s1");

        assertThat(store.getMessages("s1")).isEmpty();
        assertThat(store.getMessages("s2")).hasSize(1);
    }

    @Test
    void getMessages_isolatesSessionsBySessionId() {
        store.updateMessages("s1", List.of(UserMessage.from("Pour s1")));
        store.updateMessages("s2", List.of(UserMessage.from("Pour s2")));

        assertThat(store.getMessages("s1")).hasSize(1);
        assertThat(store.getMessages("s2")).hasSize(1);
        assertThat(((UserMessage) store.getMessages("s1").get(0)).singleText()).isEqualTo("Pour s1");
    }
}
