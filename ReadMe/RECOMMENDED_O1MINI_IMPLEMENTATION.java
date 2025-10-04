// ============================================================
// RECOMMENDED O1-MINI IMPLEMENTATION
// Best of: Your concise style + OPTIMIZED_O1MINI_PROMPTS.md
// ============================================================

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
            "max_completion_tokens", 16000  // ✅ FIXED: Increased for o1-mini (was 2000)
    );

    HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

    try {
        ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.getBody().get("choices");
        String aiResponse = (String) ((Map<String, Object>) choices.get(0).get("message")).get("content");

        return aiResponse.replaceAll("```json|```", "").trim();
    } catch (RestClientException e) {
        throw new RuntimeException("Failed to generate AI insights: " + e.getMessage(), e);
    }
}

/**
 * OPTIMIZED: Compact user prompt for o1-mini
 * Changes from your version:
 * - Removed redundant dto.prompts() (already in system prompt)
 * - Added CSV format guidance
 * - Added pattern extraction instructions
 * - Streamlined structure
 */
private String buildStructuredPrompt(ExpenseInsightsDTO dto) {
    StringBuilder prompt = new StringBuilder();

    // Concise header
    prompt.append("Analyze this expense data:\n\n");

    // Current period summary (compact)
    prompt.append("CURRENT (").append(dto.timeframe()).append("):\n");
    prompt.append("Total: ₹").append(String.format("%.2f", dto.transactionSummary().totalSpent()))
          .append(" (").append(dto.transactionSummary().transactionCount()).append(" txns)\n");
    prompt.append("Budget: ").append(dto.contextData().budgetProgressPercentage()).append("%\n\n");

    // Previous period (if exists) - compact
    if (dto.previousPeriodData() != null) {
        var prev = dto.previousPeriodData();
        double change = ((dto.transactionSummary().totalSpent() - prev.totalSpent()) / prev.totalSpent()) * 100;
        prompt.append("PREVIOUS: ₹").append(String.format("%.2f", prev.totalSpent()))
              .append(" (").append(String.format("%+.1f%%", change)).append(")\n\n");
    }

    // Top categories only (limit to 5 for token efficiency)
    prompt.append("TOP CATEGORIES:\n");
    dto.transactionSummary().categoryBreakdown().stream()
        .limit(5)  // ✅ OPTIMIZED: Top 5 only
        .forEach(cat -> prompt.append("- ").append(cat.categoryName())
            .append(": ₹").append(String.format("%.2f", cat.totalAmount()))
            .append(" (").append(String.format("%.0f%%", cat.percentage())).append(")\n"));
    prompt.append("\n");

    // Top merchants (limit to 5, with savings analysis note)
    prompt.append("TOP MERCHANTS (analyze for cheaper alternatives):\n");
    dto.transactionSummary().topMerchants().stream()
        .limit(5)  // ✅ OPTIMIZED: Top 5 only
        .forEach(m -> prompt.append("- ").append(m.merchantName())
            .append(": ₹").append(String.format("%.2f", m.totalAmount()))
            .append(" (").append(m.transactionCount()).append("× ₹")
            .append(String.format("%.0f", m.averageAmount())).append("/visit)\n"));
    prompt.append("\n");

    // CSV data with format guidance (CRITICAL for o1-mini)
    if (dto.transactionsCsv() != null && !dto.transactionsCsv().isEmpty()) {
        var meta = dto.csvMetadata();
        prompt.append("TRANSACTION CSV (").append(meta.totalTransactions()).append(" rows):\n");
        prompt.append("Format: Date,Amount,Merchant,Category,Type,Bank\n");  // ✅ ADDED: Explicit format
        prompt.append("```csv\n").append(dto.transactionsCsv()).append("\n```\n\n");

        // ✅ CRITICAL: Tell o1-mini HOW to extract patterns from Date column
        prompt.append("Extract day/time patterns from Date column (yyyy-MM-dd HH:mm:ss):\n");
        prompt.append("- Day: Parse date for Mon-Sun patterns\n");
        prompt.append("- Time: 5-11=Morning, 12-16=Afternoon, 17-20=Evening, 21-4=Night\n\n");
    }

    // Clear task with insight requirements
    prompt.append("TASK: Generate 6-8 insights:\n");
    prompt.append("- 1× spending_forecast\n");
    prompt.append("- 1× pattern_alert (day/time from CSV)\n");
    prompt.append("- 1× budget_optimization\n");
    prompt.append("- 2+× savings_opportunity (specific ₹ amounts)\n");
    prompt.append("- 1× anomaly_detection\n");

    return prompt.toString();
}

/**
 * OPTIMIZED: Concise system prompt for o1-mini reasoning model
 * Changes from your version:
 * - Added CSV format specification
 * - Clearer pattern extraction from Date column
 * - More explicit JSON structure
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
