package fr.seblaporte.kitchenvault.ai.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.nomic.NomicEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import fr.seblaporte.kitchenvault.ai.memory.PostgresChatMemoryStore;
import fr.seblaporte.kitchenvault.ai.tool.MealPlanTool;
import fr.seblaporte.kitchenvault.ai.tool.RecipeSuggestionTool;
import fr.seblaporte.kitchenvault.ai.tool.SeasonalVegetableTool;
import fr.seblaporte.kitchenvault.ai.tool.ShoppingListTool;
import fr.seblaporte.kitchenvault.config.AiProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class AiConfig {

    private static final String GROQ_BASE_URL = "https://api.groq.com/openai/v1/";

    private final AiProperties aiProperties;

    public AiConfig(AiProperties aiProperties) {
        this.aiProperties = aiProperties;
    }

    @Bean
    public ChatModel chatModel() {
        return OpenAiChatModel.builder()
                .baseUrl(GROQ_BASE_URL)
                .apiKey(aiProperties.groq().apiKey())
                .modelName(aiProperties.groq().modelName())
                .temperature(0.0)
                .build();
    }

    @Bean
    public EmbeddingModel embeddingModel() {
        return NomicEmbeddingModel.builder()
                .apiKey(aiProperties.nomic().apiKey())
                .modelName(aiProperties.nomic().modelName())
                .build();
    }

    @Bean
    public EmbeddingStore<TextSegment> embeddingStore(DataSource dataSource) {
        return PgVectorEmbeddingStore.datasourceBuilder()
                .datasource(dataSource)
                .table("recipe_embedding_store")
                .dimension(768)
                .createTable(false)
                .build();
    }

    @Bean
    public RecipeAssistant recipeAssistant(
            ChatModel chatModel,
            PostgresChatMemoryStore chatMemoryStore,
            SeasonalVegetableTool seasonalVegetableTool,
            RecipeSuggestionTool recipeSuggestionTool,
            MealPlanTool mealPlanTool,
            ShoppingListTool shoppingListTool) {
        return AiServices.builder(RecipeAssistant.class)
                .chatModel(chatModel)
                .chatMemoryProvider(sessionId -> MessageWindowChatMemory.builder()
                        .id(sessionId)
                        .maxMessages(20)
                        .chatMemoryStore(chatMemoryStore)
                        .build())
                .tools(seasonalVegetableTool, recipeSuggestionTool, mealPlanTool, shoppingListTool)
                .build();
    }
}
