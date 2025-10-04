package com.flying.solo.service;

import com.flying.solo.config.AzureOpenAIConfig;
import com.flying.solo.dto.ExpenseInsightsDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Map;

/**
 * AIInsightsService with timeout fix for o1-mini model
 * - Uses custom RestTemplate with 6-minute timeout
 * - Includes retry logic for failed calls
 * - Logs API call duration for monitoring
 */
@Slf4j
@Service
public class AIInsightsService {

    private final AzureOpenAIConfig config;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Constructor with custom RestTemplate injection
     * Uses @Qualifier to inject the timeout-configured RestTemplate
     */
    public AIInsightsService(
            AzureOpenAIConfig config,
            @Qualifier("openAIRestTemplate") RestTemplate restTemplate,  // ✅ Inject custom RestTemplate
            ObjectMapper objectMapper) {
        this.config = config;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Process expense data with o1-mini AI
     * - Retries up to 2 times on timeout/network errors
     * - Logs duration for monitoring
     * - Handles 2-5 minute processing time
     *
     * @throws JsonProcessingException if response parsing fails
     * @throws RuntimeException if all retries fail
     */
    @Retryable(
        value = {RestClientException.class, SocketTimeoutException.class},
        maxAttempts = 3,  // Initial attempt + 2 retries
        backoff = @Backoff(delay = 5000, multiplier = 2)  // 5s, 10s delays
    )
    public String processWithAI(ExpenseInsightsDTO dto) throws JsonProcessingException {
        String url = config.getEndpoint();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("api-key", config.getKey());

        String prompt = buildStructuredPrompt(dto);

        Map<String, Object> body = Map.of(
                "messages", List.of(
                        Map.of("role", "system", "content", buildSystemPrompt()),
                        Map.of("role", "user", "content", prompt)
                ),
                "max_completion_tokens", 16000
        );

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            log.info("🤖 Calling o1-mini API (this may take 2-5 minutes)...");
            long startTime = System.currentTimeMillis();

            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);

            long durationSeconds = (System.currentTimeMillis() - startTime) / 1000;
            log.info("✅ o1-mini API completed in {} seconds ({} minutes)",
                    durationSeconds, String.format("%.1f", durationSeconds / 60.0));

            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.getBody().get("choices");
            String aiResponse = (String) ((Map<String, Object>) choices.get(0).get("message")).get("content");

            return aiResponse.replaceAll("```json|```", "").trim();

        } catch (RestClientException e) {
            log.error("❌ o1-mini API call failed: {}", e.getMessage());
            throw new RuntimeException("Failed to generate AI insights: " + e.getMessage(), e);
        }
    }

    /**
     * Build compact user prompt (optimized for o1-mini)
     */
    private String buildStructuredPrompt(ExpenseInsightsDTO dto) {
        StringBuilder prompt = new StringBuilder();

        // Concise header
        prompt.append("Analyze this expense data:\n\n");

        // Current period summary
        prompt.append("CURRENT (").append(dto.timeframe()).append("):\n");
        prompt.append("Total: ₹").append(String.format("%.2f", dto.transactionSummary().totalSpent()))
              .append(" (").append(dto.transactionSummary().transactionCount()).append(" txns)\n");
        prompt.append("Budget: ").append(dto.contextData().budgetProgressPercentage()).append("%\n\n");

        // Previous period comparison
        if (dto.previousPeriodData() != null) {
            var prev = dto.previousPeriodData();
            double change = ((dto.transactionSummary().totalSpent() - prev.totalSpent()) / prev.totalSpent()) * 100;
            prompt.append("PREVIOUS: ₹").append(String.format("%.2f", prev.totalSpent()))
                  .append(" (").append(String.format("%+.1f%%", change)).append(")\n\n");
        }

        // Top categories (limit to 5)
        prompt.append("TOP CATEGORIES:\n");
        dto.transactionSummary().categoryBreakdown().stream()
            .limit(5)
            .forEach(cat -> prompt.append("- ").append(cat.categoryName())
                .append(": ₹").append(String.format("%.2f", cat.totalAmount()))
                .append(" (").append(String.format("%.0f%%", cat.percentage())).append(")\n"));
        prompt.append("\n");

        // Top merchants (limit to 5)
        prompt.append("TOP MERCHANTS (analyze for cheaper alternatives):\n");
        dto.transactionSummary().topMerchants().stream()
            .limit(5)
            .forEach(m -> prompt.append("- ").append(m.merchantName())
                .append(": ₹").append(String.format("%.2f", m.totalAmount()))
                .append(" (").append(m.transactionCount()).append("× ₹")
                .append(String.format("%.0f", m.averageAmount())).append("/visit)\n"));
        prompt.append("\n");

        // CSV data with format guidance
        if (dto.transactionsCsv() != null && !dto.transactionsCsv().isEmpty()) {
            var meta = dto.csvMetadata();
            prompt.append("TRANSACTION CSV (").append(meta.totalTransactions()).append(" rows):\n");
            prompt.append("Format: Date,Amount,Merchant,Category,Type,Bank\n");
            prompt.append("```csv\n").append(dto.transactionsCsv()).append("\n```\n\n");

            // Pattern extraction instructions
            prompt.append("Extract day/time patterns from Date column (yyyy-MM-dd HH:mm:ss):\n");
            prompt.append("- Day: Parse date for Mon-Sun patterns\n");
            prompt.append("- Time: 5-11=Morning, 12-16=Afternoon, 17-20=Evening, 21-4=Night\n\n");
        }

        // Clear task
        prompt.append("TASK: Generate 6-8 insights:\n");
        prompt.append("- 1× spending_forecast\n");
        prompt.append("- 1× pattern_alert (day/time from CSV)\n");
        prompt.append("- 1× budget_optimization\n");
        prompt.append("- 2+× savings_opportunity (specific ₹ amounts)\n");
        prompt.append("- 1× anomaly_detection\n");

        return prompt.toString();
    }

    /**
     * Build concise system prompt (optimized for o1-mini reasoning model)
     */
    private String buildSystemPrompt() {
        return """
        You are a financial analyst AI for Indian expense management.

        OBJECTIVE: Analyze transaction data and generate 6-8 actionable financial insights.

        ## Required Insight Types (MUST include ALL):

        1. **spending_forecast** (1 required)
           - Project next month's spending from patterns
           - Compare to previous period, show % change

        2. **pattern_alert** (1 required)
           - Extract day-of-week patterns from Date column (yyyy-MM-dd HH:mm:ss)
           - Parse hour for time patterns: 5-11=Morning, 12-16=Afternoon, 17-20=Evening, 21-4=Night
           - Example: "2025-08-16 14:30:00" → Saturday afternoon spending

        3. **budget_optimization** (1 required)
           - Analyze category % of total budget
           - Recommend optimal allocation

        4. **savings_opportunity** (2+ required)
           - Find merchant alternatives with lower prices
           - Calculate specific ₹ savings amounts
           - Example: "Switch from Swiggy (₹563/order) to local restaurants (₹350/order)"

        5. **anomaly_detection** (1 required)
           - Flag unusual transactions vs typical pattern
           - Quantify deviation (e.g., "100x your typical ₹500 spending")

        ## Data Sources:
        - Transaction Summary: Aggregated stats, category breakdown, top merchants
        - CSV Data (50 transactions max): Date,Amount,Merchant,Category,Type,Bank
          * Date format: yyyy-MM-dd HH:mm:ss
          * Extract day patterns by parsing date
          * Extract time patterns by parsing hour

        ## Quality Standards:
        - Use ACTUAL data (merchant names, amounts, dates from input)
        - Quantify ALL savings with ₹ amounts
        - Be specific, not generic ("Swiggy ₹563/order" not "reduce delivery")
        - Reference day/time patterns from CSV Date column

        ## Output Format (strict JSON):
        {
          "success": true,
          "insights": [
            {
              "id": "unique_id",
              "type": "spending_forecast|pattern_alert|budget_optimization|savings_opportunity|anomaly_detection",
              "title": "Specific title (max 60 chars)",
              "description": "Detailed analysis with numbers from data",
              "actionable_advice": "Concrete steps with ₹ amounts",
              "impact_amount": 0.0,
              "priority": "low|medium|high|urgent",
              "confidence_score": 0.85,
              "valid_until": null,
              "visualization_data": null
            }
          ],
          "metadata": {
            "generated_at": timestamp,
            "model_version": "o1-mini",
            "processing_time_ms": 1500,
            "total_insights": 6
          }
        }

        VALIDATION: Before returning, verify you have 1 spending_forecast, 1 pattern_alert, 1 budget_optimization, 2+ savings_opportunity, 1 anomaly_detection. Use actual data from input.
        """;
    }
}
