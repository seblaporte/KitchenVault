package fr.seblaporte.kitchenvault.ai.config;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import fr.seblaporte.kitchenvault.ai.agent.RecipeSuggestionAgent;
import fr.seblaporte.kitchenvault.ai.agent.ShoppingListConsolidationAgent;
import fr.seblaporte.kitchenvault.ai.agent.WeeklyMealPlanAgent;
import fr.seblaporte.kitchenvault.ai.memory.PostgresChatMemoryStore;
import fr.seblaporte.kitchenvault.ai.service.RoleAwareRecipeContentRetriever;
import fr.seblaporte.kitchenvault.config.AiProperties;
import fr.seblaporte.kitchenvault.service.RecipeListService;
import fr.seblaporte.kitchenvault.service.WeeklyMealPlanService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.List;

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
    public ContentRetriever weeklyPlanContentRetriever(
            EmbeddingStore<TextSegment> embeddingStore,
            EmbeddingModel embeddingModel,
            RecipeListService recipeListService) {
        // Rejected-list recipes are excluded at the vector search level (metadata filter) so
        // they're never proposable, and favorites/discovery matches are fetched via a separate
        // bounded, filtered search and tagged inline — see RoleAwareRecipeContentRetriever.
        // This replaces dumping the 3 recipe lists as raw text in the prompt, which scaled with
        // list size and could overflow the embedding model's context window.
        return new RoleAwareRecipeContentRetriever(embeddingStore, embeddingModel, recipeListService);
    }

    @Bean
    public WeeklyMealPlanAgent weeklyMealPlanAgent(
            PostgresChatMemoryStore chatMemoryStore,
            ContentRetriever weeklyPlanContentRetriever) {

        // The prompt sent to this agent is enriched with the current week plan, which is not
        // relevant as a RAG search query. RAG search must run only on the actual free-text
        // question, so we strip everything before the user message marker before it gets embedded.
        RetrievalAugmentor retrievalAugmentor = DefaultRetrievalAugmentor.builder()
                .queryTransformer(query -> {
                    String text = query.text();
                    int markerIndex = text.lastIndexOf(WeeklyMealPlanService.USER_MESSAGE_MARKER);
                    String searchText = markerIndex >= 0
                            ? text.substring(markerIndex + WeeklyMealPlanService.USER_MESSAGE_MARKER.length())
                            : text;
                    return List.of(Query.from(searchText, query.metadata()));
                })
                .contentRetriever(weeklyPlanContentRetriever)
                .build();

        return AgenticServices.agentBuilder(WeeklyMealPlanAgent.class)
                .chatModel(weeklyPlanChatModel())
                .retrievalAugmentor(retrievalAugmentor)
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
