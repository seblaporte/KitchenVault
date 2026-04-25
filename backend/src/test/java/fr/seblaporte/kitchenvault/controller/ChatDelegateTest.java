package fr.seblaporte.kitchenvault.controller;

import fr.seblaporte.kitchenvault.ai.config.RecipeAssistant;
import fr.seblaporte.kitchenvault.generated.api.ChatApiController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {ChatApiController.class})
@Import(ChatDelegate.class)
class ChatDelegateTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean RecipeAssistant assistant;

    @Test
    void sendMessage_validRequest_returns200WithReply() throws Exception {
        when(assistant.chat("session-1", "Quelles recettes de saison ?"))
                .thenReturn("Voici quelques recettes de saison…");

        mockMvc.perform(post("/api/v1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sessionId":"session-1","message":"Quelles recettes de saison ?"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("Voici quelques recettes de saison…"));
    }

    @Test
    void sendMessage_assistantThrows_returns503() throws Exception {
        when(assistant.chat("session-1", "test"))
                .thenThrow(new RuntimeException("API unavailable"));

        mockMvc.perform(post("/api/v1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sessionId":"session-1","message":"test"}
                                """))
                .andExpect(status().isServiceUnavailable());
    }
}
