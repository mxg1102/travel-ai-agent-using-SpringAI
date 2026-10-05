package com.coding.agent.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;

/**
 * Service orchestrating AI interactions via Spring AI's ChatClient.
 * Handles contextual multi-turn conversation and isolated polyglot code translations.
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final ChatClient chatClient;

    public ChatService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * Executes a chat prompt with conversation memory retained by conversation ID.
     */
    public String chat(String query, String conversationId) {
        log.info("[CHAT REQUEST] conversationId: '{}', query: '{}'", conversationId, query);
        try {
            String response = chatClient
                    .prompt()
                    .user(query)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                    .call()
                    .content();

            log.info("[CHAT RESPONSE] conversationId: '{}', response length: {} chars", 
                    conversationId, response != null ? response.length() : 0);
            return response;
        } catch (Exception e) {
            log.error("[CHAT ERROR] conversationId: '{}', error: {}", conversationId, e.getMessage(), e);
            return "I apologize, but I encountered an error while processing your request: " + e.getMessage();
        }
    }

    /**
     * Translates a code snippet to a target programming language using an isolated prompt.
     */
    public String translateCode(String code, String targetLanguage) {
        log.info("[TRANSLATE REQUEST] targetLanguage: '{}', codeLength: {} chars", 
                targetLanguage, code != null ? code.length() : 0);
        try {
            String prompt = String.format("""
                    You are an expert polyglot software engineer and compiler specialist.
                    Translate the following code into %s.

                    Strict Rules:
                    1. Output ONLY the translated code inside standard fenced code block (e.g. ```%s ... ```).
                    2. Do not include any introductory text, explanation, greeting, or markdown text outside the code block.
                    3. Include all necessary imports, class definitions, and idiomatic syntax for %s.
                    4. Maintain original logic, variables, and comments accurately.

                    Source code to translate:
                    %s
                    """, targetLanguage, targetLanguage.toLowerCase(), targetLanguage, code);

            String response = chatClient
                    .prompt()
                    .user(prompt)
                    .call()
                    .content();

            log.info("[TRANSLATE RESPONSE] targetLanguage: '{}', response received successfully", targetLanguage);
            return response;
        } catch (Exception e) {
            log.error("[TRANSLATE ERROR] targetLanguage: '{}', error: {}", targetLanguage, e.getMessage(), e);
            return "Error translating code: " + e.getMessage();
        }
    }
}
