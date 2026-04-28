package fr.seblaporte.kitchenvault.controller;

import fr.seblaporte.kitchenvault.ai.agent.RecipeSuggestionAgent;
import fr.seblaporte.kitchenvault.ai.memory.PostgresChatMemoryStore;
import fr.seblaporte.kitchenvault.generated.api.ChatApiController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.test.annotation.DirtiesContext;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {ChatApiController.class})
@Import(ChatDelegate.class)
class ChatDelegateTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean RecipeSuggestionAgent recipeSuggestionAgent;
    @MockitoBean PostgresChatMemoryStore chatMemoryStore;

    @Test
    void chatRecipe_validRequest_returns200WithReply() throws Exception {
        when(recipeSuggestionAgent.suggestRecipes("session-1", "Quelles recettes de saison ?"))
                .thenReturn("Voici quelques recettes de saison…");

        mockMvc.perform(post("/api/v1/chat/recipe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sessionId":"session-1","message":"Quelles recettes de saison ?"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("Voici quelques recettes de saison…"));
    }

    @Test
    void chatRecipe_workflowThrows_returns503() throws Exception {
        when(recipeSuggestionAgent.suggestRecipes("session-1", "test"))
                .thenThrow(new RuntimeException("API unavailable"));

        mockMvc.perform(post("/api/v1/chat/recipe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sessionId":"session-1","message":"test"}
                                """))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.BEFORE_METHOD)
    void chatRecipe_newSession_clearsMemory() throws Exception {
        when(recipeSuggestionAgent.suggestRecipes(anyString(), anyString())).thenReturn("ok");

        mockMvc.perform(post("/api/v1/chat/recipe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sessionId":"session-1","message":"premier message"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/chat/recipe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sessionId":"session-2","message":"nouvelle session"}
                                """))
                .andExpect(status().isOk());

        // deleteAllMessages appelé 2 fois : session-1 (première session) + session-2 (changement de session)
        verify(chatMemoryStore, times(2)).deleteAllMessages();
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.BEFORE_METHOD)
    void chatRecipe_sameSession_doesNotClearMemory() throws Exception {
        when(recipeSuggestionAgent.suggestRecipes(anyString(), anyString())).thenReturn("ok");

        mockMvc.perform(post("/api/v1/chat/recipe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sessionId":"session-1","message":"premier message"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/chat/recipe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sessionId":"session-1","message":"deuxième message"}
                                """))
                .andExpect(status().isOk());

        // deleteAllMessages appelé 1 seule fois (premier appel, session inconnue)
        verify(chatMemoryStore, times(1)).deleteAllMessages();
    }

}
