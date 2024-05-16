package com.example.shared

import org.slf4j.LoggerFactory
import java.net.URISyntaxException
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.nio.file.Paths
import java.util.*

object Utils {
    val OPENAI_API_KEY: String =  "demo"

    fun startConversationWith(assistant: Assistant) {
        val log = LoggerFactory.getLogger(Assistant::class.java)
        Scanner(System.`in`).use { scanner ->
            while (true) {
                log.info("==================================================")
                log.info("User: ")
                val userQuery = scanner.nextLine()
                log.info("==================================================")

                if ("exit".equals(userQuery, ignoreCase = true)) {
                    break
                }

                val agentAnswer = assistant.answer(userQuery)
                log.info("==================================================")
                log.info("Assistant: $agentAnswer")
            }
        }
    }

    fun glob(glob: String): PathMatcher {
        return FileSystems.getDefault().getPathMatcher("glob:$glob")
    }

}
