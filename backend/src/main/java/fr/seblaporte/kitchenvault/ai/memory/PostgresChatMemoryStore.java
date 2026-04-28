package fr.seblaporte.kitchenvault.ai.memory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.*;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class PostgresChatMemoryStore implements ChatMemoryStore {

    private static final Logger log = LoggerFactory.getLogger(PostgresChatMemoryStore.class);

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public PostgresChatMemoryStore(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String sessionId = memoryId.toString();
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT role, content FROM chat_message WHERE session_id = ? ORDER BY created_at ASC",
                sessionId);

        List<ChatMessage> messages = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String role = (String) row.get("role");
            String content = (String) row.get("content");
            try {
                messages.add(deserialize(role, content));
            } catch (Exception e) {
                log.warn("Skipping unreadable chat message for session {}: {}", sessionId, e.getMessage());
            }
        }
        return messages;
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        String sessionId = memoryId.toString();
        jdbc.update("DELETE FROM chat_message WHERE session_id = ?", sessionId);
        for (ChatMessage message : messages) {
            try {
                String role = roleOf(message);
                String content = serialize(message);
                jdbc.update(
                        "INSERT INTO chat_message (session_id, role, content) VALUES (?, ?, ?)",
                        sessionId, role, content);
            } catch (Exception e) {
                log.error("Failed to persist chat message for session {}: {}", sessionId, e.getMessage());
            }
        }
    }

    @Override
    public void deleteMessages(Object memoryId) {
        jdbc.update("DELETE FROM chat_message WHERE session_id = ?", memoryId.toString());
    }

    public void deleteAllMessages() {
        jdbc.update("DELETE FROM chat_message");
    }

    private String roleOf(ChatMessage message) {
        if (message instanceof UserMessage) return "USER";
        if (message instanceof AiMessage) return "AI";
        if (message instanceof ToolExecutionResultMessage) return "TOOL_EXECUTION_RESULT";
        if (message instanceof SystemMessage) return "SYSTEM";
        return message.getClass().getSimpleName().toUpperCase();
    }

    private String serialize(ChatMessage message) throws JsonProcessingException {
        if (message instanceof UserMessage m) {
            return objectMapper.writeValueAsString(Map.of("text", m.singleText()));
        }
        if (message instanceof AiMessage m) {
            if (m.hasToolExecutionRequests()) {
                List<Map<String, String>> toolRequests = m.toolExecutionRequests().stream()
                        .map(r -> {
                            Map<String, String> map = new java.util.HashMap<>();
                            map.put("id", r.id());
                            map.put("name", r.name());
                            map.put("arguments", r.arguments() != null ? r.arguments() : "{}");
                            return map;
                        })
                        .toList();
                Map<String, Object> payload = new java.util.HashMap<>();
                payload.put("text", m.text() != null ? m.text() : "");
                payload.put("toolRequests", toolRequests);
                return objectMapper.writeValueAsString(payload);
            }
            return objectMapper.writeValueAsString(Map.of("text", m.text() != null ? m.text() : ""));
        }
        if (message instanceof ToolExecutionResultMessage m) {
            return objectMapper.writeValueAsString(Map.of(
                    "id", m.id(), "toolName", m.toolName(), "text", m.text()));
        }
        if (message instanceof SystemMessage m) {
            return objectMapper.writeValueAsString(Map.of("text", m.text()));
        }
        return objectMapper.writeValueAsString(Map.of("raw", message.toString()));
    }

    @SuppressWarnings("unchecked")
    private ChatMessage deserialize(String role, String content) throws JsonProcessingException {
        Map<String, Object> data = objectMapper.readValue(content, Map.class);
        return switch (role) {
            case "USER" -> UserMessage.from((String) data.get("text"));
            case "AI" -> {
                String text = (String) data.get("text");
                if (data.containsKey("toolRequests")) {
                    List<Map<String, Object>> rawRequests = (List<Map<String, Object>>) data.get("toolRequests");
                    List<ToolExecutionRequest> requests = rawRequests.stream()
                            .map(r -> ToolExecutionRequest.builder()
                                    .id((String) r.get("id"))
                                    .name((String) r.get("name"))
                                    .arguments((String) r.get("arguments"))
                                    .build())
                            .toList();
                    yield AiMessage.from(requests);
                }
                yield AiMessage.from(text != null ? text : "");
            }
            case "TOOL_EXECUTION_RESULT" -> ToolExecutionResultMessage.from(
                    (String) data.get("id"),
                    (String) data.get("toolName"),
                    (String) data.get("text"));
            case "SYSTEM" -> SystemMessage.from((String) data.get("text"));
            default -> UserMessage.from(content);
        };
    }
}
