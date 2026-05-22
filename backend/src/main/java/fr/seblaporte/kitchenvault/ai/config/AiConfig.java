package fr.seblaporte.kitchenvault.ai.config;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import fr.seblaporte.kitchenvault.ai.agent.RecipeSuggestionAgent;
import fr.seblaporte.kitchenvault.ai.agent.ShoppingListConsolidationAgent;
import fr.seblaporte.kitchenvault.ai.agent.WeeklyMealPlanAgent;
import fr.seblaporte.kitchenvault.ai.memory.PostgresChatMemoryStore;
import fr.seblaporte.kitchenvault.config.AiProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class AiConfig {

    private final AiProperties aiProperties;

    public AiConfig(AiProperties aiProperties) {
        this.aiProperties = aiProperties;
    }

    @Bean
    public ChatModel chatModel() {
        return OpenAiChatModel.builder()
                .baseUrl(aiProperties.ovh().baseUrl())
                .apiKey(aiProperties.ovh().apiKey())
                .modelName(aiProperties.ovh().modelName())
                .logRequests(true)
                .logResponses(true)
                .temperature(0.0)
                .build();
    }

    @Bean
    public EmbeddingModel embeddingModel() {
        return OpenAiEmbeddingModel.builder()
                .baseUrl(aiProperties.ovhEmbedding().baseUrl())
                .apiKey(aiProperties.ovhEmbedding().apiKey())
                .modelName(aiProperties.ovhEmbedding().modelName())
                .build();
    }

    @Bean
    public EmbeddingStore<TextSegment> embeddingStore(DataSource dataSource) {
        return PgVectorEmbeddingStore.datasourceBuilder()
                .datasource(dataSource)
                .table("recipe_embedding_store")
                .dimension(aiProperties.ovhEmbedding().dimension())
                .createTable(false)
                .build();
    }

    @Bean
    public EmbeddingStoreContentRetriever contentRetriever(
            EmbeddingStore<TextSegment> embeddingStore,
            EmbeddingModel embeddingModel) {
        return EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(10)
                .minScore(0.6)
                .build();
    }

    @Bean
    public RecipeSuggestionAgent recipeSuggestionAgent(
            ChatModel chatModel,
            PostgresChatMemoryStore chatMemoryStore,
            EmbeddingStoreContentRetriever contentRetriever) {

        return AgenticServices.agentBuilder(RecipeSuggestionAgent.class)
                .chatModel(chatModel)
                .contentRetriever(contentRetriever)
                .chatMemoryProvider(sessionId -> MessageWindowChatMemory.builder()
                        .id(sessionId)
                        .maxMessages(20)
                        .chatMemoryStore(chatMemoryStore)
                        .build())
                .build();
    }

    @Bean
    public ChatModel weeklyPlanChatModel() {
        return OpenAiChatModel.builder()
                .baseUrl(aiProperties.ovh().baseUrl())
                .apiKey(aiProperties.ovh().apiKey())
                .modelName(aiProperties.ovh().modelName())
                .logRequests(true)
                .logResponses(true)
                .temperature(0.3)
                .build();
    }

    @Bean
    public WeeklyMealPlanAgent weeklyMealPlanAgent(
            PostgresChatMemoryStore chatMemoryStore,
            EmbeddingStoreContentRetriever contentRetriever) {

        return AgenticServices.agentBuilder(WeeklyMealPlanAgent.class)
                .chatModel(weeklyPlanChatModel())
                .contentRetriever(contentRetriever)
                .chatMemoryProvider(sessionId -> MessageWindowChatMemory.builder()
                        .id(sessionId)
                        .maxMessages(40)
                        .chatMemoryStore(chatMemoryStore)
                        .build())
                .build();
    }

    @Bean
    public ShoppingListConsolidationAgent shoppingListConsolidationAgent() {
        return AgenticServices.agentBuilder(ShoppingListConsolidationAgent.class)
                .chatModel(chatModel())
                .build();
    }

}
