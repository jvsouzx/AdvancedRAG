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
import dev.langchain4j.model.input.PromptTemplate
import dev.langchain4j.model.openai.OpenAiChatModel
import dev.langchain4j.rag.DefaultRetrievalAugmentor
import dev.langchain4j.rag.RetrievalAugmentor
import dev.langchain4j.rag.content.retriever.ContentRetriever
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever
import dev.langchain4j.rag.query.router.QueryRouter
import dev.langchain4j.service.AiServices
import dev.langchain4j.store.embedding.EmbeddingStore
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore
import java.util.*


object _06_Advanced_RAG_Skip_Retrieval_Example {
    /**
     * Please refer to [Naive_RAG_Example] for a basic context.
     *
     *
     * Advanced RAG in LangChain4j is described here: https://github.com/langchain4j/langchain4j/pull/538
     *
     *
     * This example demonstrates how to conditionally skip retrieval.
     * Sometimes, retrieval is unnecessary, for instance, when a user simply says "Hi".
     *
     *
     * There are multiple ways to implement this, but the simplest one is to use a custom [QueryRouter].
     * When retrieval should be skipped, QueryRouter will return an empty list,
     * meaning that the query will not be routed to any [ContentRetriever].
     *
     *
     * Decision-making can be implemented in various ways:
     * - Using rules (e.g., depending on the user's privileges, location, etc.).
     * - Using keywords (e.g., if a query contains specific words).
     * - Using semantic similarity (see EmbeddingModelTextClassifierExample in this repository).
     * - Using an LLM to make a decision.
     *
     *
     * In this example, we will use an LLM to decide whether a user query should do retrieval or not.
     */
    @JvmStatic
    fun main(args: Array<String>) {
        val assistant = createAssistant()

        // First, say "Hi"
        // Notice how this query is not routed to any retrievers.

        // Now, ask "Can I cancel my reservation?"
        // This query has been routed to our retriever.
        startConversationWith(assistant)
    }

    private fun createAssistant(): Assistant {
        val embeddingModel: EmbeddingModel = BgeSmallEnV15QuantizedEmbeddingModel()

        val embeddingStore: EmbeddingStore<TextSegment> =
            embed("src/main/resources/miles-of-smiles-terms-of-use.txt", embeddingModel)

        val contentRetriever: ContentRetriever = EmbeddingStoreContentRetriever.builder()
            .embeddingStore(embeddingStore)
            .embeddingModel(embeddingModel)
            .maxResults(2)
            .minScore(0.6)
            .build()

        val chatLanguageModel: ChatLanguageModel = OpenAiChatModel.builder()
            .apiKey(OPENAI_API_KEY)
            .build()

        // Let's create a query router.
        val queryRouter = object : QueryRouter {
            private val PROMPT_TEMPLATE: PromptTemplate = PromptTemplate.from(
                "Is the following query related to the business of the car rental company? " +
                        "Answer only 'yes', 'no' or 'maybe'. " +
                        "Query: {{it}}"
            )

            override fun route(query: dev.langchain4j.rag.query.Query?): List<ContentRetriever> {
                val prompt = PROMPT_TEMPLATE.apply(query)

                val aiMessage = chatLanguageModel.generate(prompt.toUserMessage()).content()
                println("LLM decided: " + aiMessage.text())

                if (aiMessage.text().lowercase().contains("no")) {
                    return kotlin.collections.emptyList()
                }

                return listOf(contentRetriever)
            }
        }

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