package com.sy.course_system.agent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.sy.course_system.config.AgentProperties;

@Component
public class OpenAiCompatibleAgentLlmClient implements AgentLlmClient {

    private final AgentProperties properties;
    private final RestTemplate restTemplate;

    public OpenAiCompatibleAgentLlmClient(AgentProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.connectTimeoutMs());
        factory.setReadTimeout(properties.readTimeoutMs());
        this.restTemplate = new RestTemplate(factory);
    }

    @Override
    public String chat(AgentLlmRequest request) {
        if (properties.useMockClient()) {
            return mockAnswer(request);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(properties.apiKey());

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", request.getSystemPrompt()));
        for (AgentLlmMessage message : safeMessages(request)) {
            if (message.getContent() != null && !message.getContent().isBlank()) {
                messages.add(Map.of("role", message.getRole(), "content", message.getContent()));
            }
        }

        Map<String, Object> body = new HashMap<>();
        body.put("model", properties.model());
        body.put("messages", messages);
        body.put("temperature", properties.temperature());
        body.put("max_tokens", properties.maxOutputTokens());

        @SuppressWarnings("rawtypes")
        Map response = restTemplate.postForObject(
                normalizeBaseUrl(properties.baseUrl()) + "/chat/completions",
                new HttpEntity<>(body, headers),
                Map.class);
        String content = extractContent(response);
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("模型服务返回空内容");
        }
        return content.trim();
    }

    private List<AgentLlmMessage> safeMessages(AgentLlmRequest request) {
        return request.getMessages() == null ? List.of() : request.getMessages();
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "https://api.openai.com/v1";
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    @SuppressWarnings("rawtypes")
    private String extractContent(Map response) {
        if (response == null) {
            return null;
        }
        Object choicesValue = response.get("choices");
        if (!(choicesValue instanceof List choices) || choices.isEmpty()) {
            return null;
        }
        Object firstChoice = choices.get(0);
        if (!(firstChoice instanceof Map choiceMap)) {
            return null;
        }
        Object messageValue = choiceMap.get("message");
        if (messageValue instanceof Map messageMap) {
            Object content = messageMap.get("content");
            return content == null ? null : String.valueOf(content);
        }
        Object text = choiceMap.get("text");
        return text == null ? null : String.valueOf(text);
    }

    private String mockAnswer(AgentLlmRequest request) {
        String question = latestUserQuestion(request);
        String summary = request.getFallbackSummary();
        StringBuilder answer = new StringBuilder();
        answer.append("我先基于系统里已有的学习数据给出一个只读建议。");
        if (summary != null && !summary.isBlank()) {
            answer.append("\n\n").append(summary);
        }
        answer.append("\n\n针对你的问题：").append(question);
        answer.append("\n\n建议先查看推荐分较高且准备度较好的课程；如果存在薄弱前置知识点，先补齐这些知识点再进入难度更高的课程。当前助手不会替你选课或修改学习进度，只提供学习路径和课程选择建议。");
        return answer.toString();
    }

    private String latestUserQuestion(AgentLlmRequest request) {
        List<AgentLlmMessage> messages = safeMessages(request);
        for (int i = messages.size() - 1; i >= 0; i--) {
            AgentLlmMessage message = messages.get(i);
            if ("user".equals(message.getRole()) && message.getContent() != null && !message.getContent().isBlank()) {
                return message.getContent();
            }
        }
        return "请给出下一步学习建议";
    }
}
