package com.example

import com.example.shared.Assistant
import com.example.shared.Utils.OPENAI_API_KEY
import com.example.shared.Utils.startConversationWith
import dev.langchain4j.data.document.Document
import dev.langchain4j.data.document.DocumentSplitter
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader.loadDocument
import dev.langchain4j.data.document.parser.TextDocumentParser
import dev.langchain4j.data.document.splitter.DocumentSplitters
import dev.langchain4j.data.embedding.Embedding
import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.memory.chat.MessageWindowChatMemory
import dev.langchain4j.model.chat.ChatLanguageModel
import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.model.embedding.bge.small.en.v15.BgeSmallEnV15QuantizedEmbeddingModel
import dev.langchain4j.model.openai.OpenAiChatModel
import dev.langchain4j.rag.DefaultRetrievalAugmentor
import dev.langchain4j.rag.RetrievalAugmentor
import dev.langchain4j.rag.content.retriever.ContentRetriever
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever
import dev.langchain4j.rag.query.router.LanguageModelQueryRouter
import dev.langchain4j.rag.query.router.QueryRouter
import dev.langchain4j.service.AiServices
import dev.langchain4j.store.embedding.EmbeddingStore
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore

object _02_Advanced_RAG_with_Query_Routing_Example {
    /**
     * Please refer to [Naive_RAG_Example] for a basic context.
     *
     *
     * Advanced RAG in LangChain4j is described here: https://github.com/langchain4j/langchain4j/pull/538
     *
     *
     * This example showcases the implementation of a more advanced RAG application
     * using a technique known as "query routing".
     *
     *
     * Often, private data is spread across multiple sources and formats.
     * This might include internal company documentation on Confluence, your project's code in a Git repository,
     * a relational database with user data, or a search engine with the products you sell, among others.
     * In a RAG flow that utilizes data from multiple sources, you will likely have multiple
     * [EmbeddingStore]s or [ContentRetriever]s.
     * While you could route each user query to all available [ContentRetriever]s,
     * this approach might be inefficient and counterproductive.
     *
     *
     * "Query routing" is the solution to this challenge. It involves directing a query to the most appropriate
     * [ContentRetriever] (or several). Routing can be implemented in various ways:
     * - Using rules (e.g., depending on the user's privileges, location, etc.).
     * - Using keywords (e.g., if a query contains words X1, X2, X3, route it to [ContentRetriever] X, etc.).
     * - Using semantic similarity (see EmbeddingModelTextClassifierExample in this repository).
     * - Using an LLM to make a routing decision.
     *
     *
     * For scenarios 1, 2, and 3, you can implement a custom [QueryRouter].
     * For scenario 4, this example will demonstrate how to use a [LanguageModelQueryRouter].
     */
    @JvmStatic
    fun main(args: Array<String>) {
        val assistant = createAssistant()

        // First, ask "What is the legacy of John Doe?"
        // Then, ask "Can I cancel my reservation?"
        // Now, see the logs to observe how the queries are routed to different retrievers.
        startConversationWith(assistant)
    }

    private fun createAssistant(): Assistant {
        val embeddingModel: EmbeddingModel = BgeSmallEnV15QuantizedEmbeddingModel()

        // Let's create a separate embedding store specifically for biographies.
        val biographyEmbeddingStore: EmbeddingStore<TextSegment> =
            embed("src/main/resources/biography-of-john-doe.txt", embeddingModel)
        val biographyContentRetriever: ContentRetriever = EmbeddingStoreContentRetriever.builder()
            .embeddingStore(biographyEmbeddingStore)
            .embeddingModel(embeddingModel)
            .maxResults(2)
            .minScore(0.6)
            .build()

        // Additionally, let's create a separate embedding store dedicated to terms of use.
        val termsOfUseEmbeddingStore: EmbeddingStore<TextSegment> =
            embed("src/main/resources/miles-of-smiles-terms-of-use.txt", embeddingModel)
        val termsOfUseContentRetriever: ContentRetriever = EmbeddingStoreContentRetriever.builder()
            .embeddingStore(termsOfUseEmbeddingStore)
            .embeddingModel(embeddingModel)
            .maxResults(2)
            .minScore(0.6)
            .build()

        val chatLanguageModel: ChatLanguageModel = OpenAiChatModel.builder()
            .apiKey(OPENAI_API_KEY)
            .build()

        // Let's create a query router.
        val retrieverToDescription: MutableMap<ContentRetriever, String> = HashMap<ContentRetriever, String>()
        retrieverToDescription[biographyContentRetriever] = "biography of John Doe"
        retrieverToDescription[termsOfUseContentRetriever] = "terms of use of car rental company"
        val queryRouter: QueryRouter = LanguageModelQueryRouter(chatLanguageModel, retrieverToDescription)

        val retrievalAugmentor: RetrievalAugmentor = DefaultRetrievalAugmentor.builder()
            .queryRouter(queryRouter)
            .build()

        return AiServices.builder(Assistant::class.java)
            .chatLanguageModel(chatLanguageModel)
            .retrievalAugmentor(retrievalAugmentor)
            .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
            .build()
    }

    private fun embed(documentPath: String, embeddingModel: EmbeddingModel): EmbeddingStore<TextSegment> {
        val documentParser: TextDocumentParser = TextDocumentParser()
        val document: Document = loadDocument(documentPath, documentParser)

        val splitter: DocumentSplitter = DocumentSplitters.recursive(300, 0)
        val segments: List<TextSegment> = splitter.split(document)

        val embeddings: List<Embedding> = embeddingModel.embedAll(segments).content()

        val embeddingStore: EmbeddingStore<TextSegment> = InMemoryEmbeddingStore()
        embeddingStore.addAll(embeddings, segments)
        return embeddingStore
    }
}