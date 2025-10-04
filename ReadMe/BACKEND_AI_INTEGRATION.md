# Backend AI Integration Guide - Complete Implementation

## 📋 Overview

This document provides the **complete, production-ready** backend implementation for processing AI insights with CSV transaction data from the Android Expense Manager app. It includes mandatory savings insights and top merchant analysis.

---

## 🏗️ Architecture

```
Android App → ExpenseInsightsDTO (JSON) → Spring Boot Backend → Azure OpenAI → Structured Insights
```

---

## 📦 Complete Backend Service Implementation

### **AIInsightsService.java** - Full Implementation

```java
package com.flying.solo.service;

import com.flying.solo.dto.ExpenseInsightsDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class AIInsightsService {

    private final AzureOpenAIConfig config;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public AIInsightsService(AzureOpenAIConfig config,
                            RestTemplate restTemplate,
                            ObjectMapper objectMapper) {
        this.config = config;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Main method to process expense data with AI and generate insights
     */
    public String processWithAI(ExpenseInsightsDTO dto) throws JsonProcessingException {
        String url = config.getEndpoint();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("api-key", config.getKey());

        // Build structured prompt with all data
        String prompt = buildStructuredPrompt(dto);

        log.info("Processing AI request - Transactions: {}, CSV Size: {}KB",
                 dto.csvMetadata() != null ? dto.csvMetadata().totalTransactions() : 0,
                 dto.csvMetadata() != null ? dto.csvMetadata().csvSizeBytes() / 1024 : 0);

        Map<String, Object> body = Map.of(
                "messages", List.of(
                        Map.of("role", "system", "content", buildSystemPrompt()),
                        Map.of("role", "user", "content", prompt)
                ),
                "max_tokens", 2500,
                "temperature", 0.7
        );

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.getBody().get("choices");
            String aiResponse = (String) ((Map<String, Object>) choices.get(0).get("message")).get("content");

            // Clean response (remove markdown code blocks if present)
            String cleanResponse = aiResponse
                    .replaceAll("```json", "")
                    .replaceAll("```", "")
                    .trim();

            log.info("AI insights generated successfully");
            return cleanResponse;

        } catch (RestClientException e) {
            log.error("AI API call failed", e);
            throw new RuntimeException("Failed to generate AI insights: " + e.getMessage(), e);
        }
    }

    /**
     * Build structured prompt with all sections optimized for AI understanding
     * INCLUDES: Mandatory savings analysis and top merchant requirements
     */
    private String buildStructuredPrompt(ExpenseInsightsDTO dto) {
        StringBuilder prompt = new StringBuilder();

        // ==================== SECTION 1: Analysis Instructions ====================
        prompt.append("# Financial Analysis Request\n\n");
        prompt.append("## Instructions\n");
        for (String instruction : dto.prompts()) {
            prompt.append("- ").append(instruction).append("\n");
        }

        // MANDATORY REQUIREMENTS - Forces AI to generate savings insights
        prompt.append("\n## MANDATORY Requirements:\n");
        prompt.append("1. **MUST include 'savings_opportunity' insights** - Analyze top merchants for cheaper alternatives\n");
        prompt.append("2. **MUST analyze top 3-5 merchants individually** - Compare pricing, suggest alternatives\n");
        prompt.append("3. **MUST use CSV data** - Identify day/time patterns for targeted savings recommendations\n");
        prompt.append("4. **MUST quantify savings** - Every savings insight should include specific ₹ amounts\n");
        prompt.append("5. **MUST be specific** - No generic advice, use actual merchant names, amounts, and patterns from data\n\n");

        // ==================== SECTION 2: Current Period Summary ====================
        prompt.append("## Current Period Summary\n");
        prompt.append("- **Timeframe**: ").append(dto.timeframe()).append("\n");
        prompt.append("- **Total Spent**: ₹").append(String.format("%.2f", dto.transactionSummary().totalSpent())).append("\n");
        prompt.append("- **Transaction Count**: ").append(dto.transactionSummary().transactionCount()).append("\n");
        prompt.append("- **Budget Progress**: ").append(dto.contextData().budgetProgressPercentage()).append("%\n");
        prompt.append("- **Days Remaining**: ").append(dto.contextData().daysRemainingInMonth()).append("\n\n");

        // ==================== SECTION 3: Previous Period Comparison ====================
        if (dto.previousPeriodData() != null) {
            var prev = dto.previousPeriodData();
            prompt.append("## Previous Period Comparison (").append(prev.periodLabel()).append(")\n");
            prompt.append("- **Previous Spent**: ₹").append(String.format("%.2f", prev.totalSpent())).append("\n");
            prompt.append("- **Previous Transactions**: ").append(prev.transactionCount()).append("\n");

            double change = ((dto.transactionSummary().totalSpent() - prev.totalSpent()) / prev.totalSpent()) * 100;
            String changeSymbol = change > 0 ? "↑" : "↓";
            prompt.append("- **Change**: ").append(changeSymbol).append(" ").append(String.format("%.1f%%", Math.abs(change))).append("\n");
            prompt.append("- **Avg Daily Spending**: ₹").append(String.format("%.2f", prev.averageDailySpending())).append("\n\n");
        }

        // ==================== SECTION 4: Category Breakdown ====================
        prompt.append("## Category Breakdown\n");
        for (var category : dto.transactionSummary().categoryBreakdown()) {
            prompt.append("- **").append(category.categoryName()).append("**: ")
                  .append("₹").append(String.format("%.2f", category.totalAmount()))
                  .append(" (").append(category.transactionCount()).append(" txns, ")
                  .append("avg ₹").append(String.format("%.2f", category.averagePerTransaction())).append(")\n");
        }
        prompt.append("\n");

        // ==================== SECTION 5: Top Merchants (WITH EXPLICIT SAVINGS ANALYSIS) ====================
        prompt.append("## Top Merchants (ANALYZE FOR SAVINGS OPPORTUNITIES)\n");
        prompt.append("**CRITICAL**: For EACH top merchant below, you MUST:\n");
        prompt.append("1. Analyze if their pricing is premium/expensive\n");
        prompt.append("2. Suggest specific cheaper alternatives\n");
        prompt.append("3. Calculate exact savings potential in ₹\n");
        prompt.append("4. Consider frequency - are they visiting too often?\n\n");

        for (var merchant : dto.transactionSummary().topMerchants()) {
            prompt.append("- **").append(merchant.merchantName()).append("**: ")
                  .append("₹").append(String.format("%.2f", merchant.totalAmount()))
                  .append(" (").append(merchant.transactionCount()).append(" visits, ")
                  .append("avg ₹").append(String.format("%.2f", merchant.averageAmount())).append("/visit, ")
                  .append("category: ").append(merchant.categoryName()).append(")\n");
        }

        prompt.append("\n⚠️ **REQUIRED**: Generate at least TWO 'savings_opportunity' insights:\n");
        prompt.append("   - One analyzing the TOP merchant with alternatives (e.g., 'Swiggy vs local restaurants')\n");
        prompt.append("   - One analyzing spending frequency patterns (e.g., 'Ordering too often - consider meal prep')\n\n");

        // ==================== SECTION 6: Monthly Trends ====================
        prompt.append("## Monthly Trends\n");
        for (var trend : dto.transactionSummary().monthlyTrends()) {
            prompt.append("- **").append(trend.month()).append("**: ")
                  .append("₹").append(String.format("%.2f", trend.totalAmount()))
                  .append(" (").append(trend.transactionCount()).append(" txns, ")
                  .append("avg ₹").append(String.format("%.2f", trend.averagePerTransaction())).append(")\n");
        }
        prompt.append("\n");

        // ==================== SECTION 7: Detailed Transaction Data (CSV) ====================
        if (dto.transactionsCsv() != null && !dto.transactionsCsv().isEmpty() && dto.csvMetadata() != null) {
            var meta = dto.csvMetadata();
            prompt.append("## Detailed Transaction History (").append(meta.totalTransactions()).append(" transactions)\n");
            prompt.append("**Date Range**: ").append(meta.dateRangeStart()).append(" to ").append(meta.dateRangeEnd()).append("\n");
            prompt.append("**CSV Size**: ").append(meta.csvSizeBytes() / 1024).append("KB\n");
            prompt.append("**Includes Categories**: ").append(meta.includesCategories() ? "Yes" : "No").append("\n");
            prompt.append("**Includes Time Analysis**: ").append(meta.includesTimeAnalysis() ? "Yes" : "No").append("\n\n");

            prompt.append("```csv\n");
            prompt.append(dto.transactionsCsv());
            prompt.append("\n```\n\n");

            prompt.append("**CSV Analysis Instructions**: Use this data to identify:\n");
            prompt.append("- Day-of-week spending patterns (e.g., 'You spend ₹1,200 every Saturday on food delivery')\n");
            prompt.append("- Time-of-day spending habits (e.g., 'Late-night Swiggy orders 8-10 PM cost ₹500 avg')\n");
            prompt.append("- Merchant frequency patterns (e.g., 'Visiting HungerBox 4x/week at ₹150/meal = ₹2,400/month')\n");
            prompt.append("- Category trends over time (e.g., 'Food spending increased 30% in last 3 months')\n");
            prompt.append("- Anomalous transactions (e.g., 'One-time ₹50,000 transaction to AKSHAYAKALPA')\n");
            prompt.append("- Recurring expenses and timing (e.g., 'Grocery shopping every 1st of month at ₹3,000')\n\n");
        }

        // ==================== SECTION 8: User Preferences ====================
        var prefs = dto.contextData().userPreferences();
        prompt.append("## User Preferences\n");
        prompt.append("- **Currency**: ").append(prefs.currency()).append("\n");
        prompt.append("- **Primary Categories**: ").append(String.join(", ", prefs.primaryCategories())).append("\n");
        prompt.append("- **Risk Tolerance**: ").append(prefs.riskTolerance()).append("\n\n");

        return prompt.toString();
    }

    /**
     * Enhanced system prompt with MANDATORY savings insight requirements
     * This ensures AI always generates savings_opportunity insights
     */
    private String buildSystemPrompt() {
        return """
            You are an expert financial advisor AI specialized in personal expense analysis for Indian users.

            ## Your Core Mission:
            Analyze transaction data and provide SPECIFIC, ACTIONABLE insights that help users save money.
            Your insights must be derived from ACTUAL data patterns, not generic advice.

            ## Your Responsibilities:
            1. Analyze transaction data comprehensively (summary statistics AND detailed CSV)
            2. Identify spending patterns: day-of-week trends, time-of-day habits, merchant frequency
            3. **MANDATORY**: Find concrete savings opportunities by analyzing merchant pricing
            4. Detect anomalies, unusual transactions, and wasteful spending
            5. Provide actionable recommendations with specific steps and ₹ amounts
            6. Compare current spending with previous periods to show trends
            7. Consider Indian context: festivals, salary cycles (1st/last day), typical spending patterns

            ## CSV Data Analysis Guidelines:
            The CSV contains these columns with rich data for pattern detection:
            - **Date**: Transaction date and time (identify timing patterns)
            - **Amount**: Transaction amount in INR (spot anomalies, calculate averages)
            - **Merchant**: Merchant name (analyze loyalty, frequency, pricing)
            - **Category**: Expense category (track category trends)
            - **Type**: Debit or Credit (focus on debits for savings)
            - **Bank**: Bank name (multi-bank user analysis)
            - **DayOfWeek**: Monday-Sunday (weekly pattern detection)
            - **TimeOfDay**: Morning/Afternoon/Evening/Night (time-based habit analysis)

            **Pattern Detection Requirements:**
            - Weekly patterns: "You spend ₹1,500 every Saturday on food delivery"
            - Time-based habits: "Late-night orders (9-11 PM) cost 40% more than lunch orders"
            - Merchant loyalty: "HungerBox 15 visits/month at ₹150/meal = ₹2,250"
            - Seasonal trends: "Grocery spending spikes 25% mid-month around 15th"
            - Anomalies: "₹50,000 transaction is 100x your typical spending"
            - Recurring expenses: "Monthly Netflix ₹650 on 5th, Amazon Prime ₹1,500 on 1st"

            ## Output Format (CRITICAL - Must Follow Exactly):
            Return a valid JSON object with this structure:

            {
              "success": true,
              "insights": [
                {
                  "id": "unique_id_string",
                  "type": "spending_forecast|pattern_alert|budget_optimization|savings_opportunity|anomaly_detection",
                  "title": "Clear, specific title (max 60 characters)",
                  "description": "Detailed explanation with SPECIFIC numbers, merchants, days, times from actual data",
                  "actionable_advice": "Concrete steps with SPECIFIC actions and quantified savings",
                  "impact_amount": 0.0,
                  "priority": "low|medium|high|urgent",
                  "confidence_score": 0.85,
                  "valid_until": null,
                  "visualization_data": null
                }
              ],
              "metadata": {
                "generated_at": 1234567890,
                "model_version": "gpt-4",
                "processing_time_ms": 1500,
                "total_insights": 6
              }
            }

            ## Insight Generation Rules:
            1. **Generate 6-8 insights minimum** (more if data supports it)
            2. **Prioritize CSV-derived insights** (patterns invisible in summaries)
            3. **Use SPECIFIC data points**: actual merchant names, exact amounts, specific days/times
            4. **Compare with previous period** when available
            5. **Make recommendations ACTIONABLE**: specific steps, not vague advice
            6. **Use Indian Rupee symbol ₹** for all amounts
            7. **Consider Indian context**: salary dates (1st/last), festivals, typical patterns
            8. **Assign realistic confidence scores** based on data quality (0.75-0.95 range)
            9. **Set priority based on financial impact**: >₹5000 = high, >₹10000 = urgent
            10. **NO generic advice** - every insight must reference specific data

            ## 🚨 MANDATORY Insight Types (MUST INCLUDE):

            ### 1. savings_opportunity (REQUIRED - AT LEAST 2):
            **You MUST generate at least 2 savings_opportunity insights analyzing:**

            #### A. Top Merchant Pricing Analysis:
            - Compare top merchant costs vs cheaper alternatives
            - Example: "You spend ₹8,450 at Swiggy (₹563/order). Local restaurants offer same meals at ₹300-350. Switching 10 orders saves ₹2,500/month"

            #### B. Frequency-Based Savings:
            - Identify excessive visit frequency
            - Example: "HungerBox 18 visits/month (₹150/meal = ₹2,700). Reduce to 10 visits + pack lunch 8 days saves ₹1,200/month"

            #### C. Premium vs Budget Alternatives:
            - Spot premium services with budget alternatives
            - Example: "Zepto quick commerce charges ₹50 delivery + 15% markup. BigBasket scheduled delivery saves ₹800/month"

            **Format Requirements:**
            - **type**: MUST be "savings_opportunity"
            - **description**: Include current spending, merchant name, frequency, average cost
            - **actionable_advice**: Specific alternative with exact savings calculation
            - **impact_amount**: Quantified monthly savings in ₹

            ### 2. Top Merchant Analysis (REQUIRED - AT LEAST 1):
            **You MUST analyze the top 3-5 merchants individually:**

            - **Pricing Analysis**: Is this merchant expensive? Compare to market rates
            - **Frequency Analysis**: Visiting too often? Calculate monthly impact
            - **Alternative Suggestions**: Name specific cheaper alternatives
            - **Loyalty Programs**: Mention if merchant offers discounts/loyalty benefits

            Example:
            - "Swiggy (₹8,450, 15 orders): ₹563/order is 60% more than local restaurants (₹350). Use Swiggy 5x/month for convenience, cook/order locally for rest. Saves ₹3,200/month"

            ## Example High-Quality Insights:

            ### ✅ GOOD - Pattern Alert (Specific, Data-Driven):
            {
              "id": "pattern_weekend_food_delivery_aug2025",
              "type": "pattern_alert",
              "title": "Weekend food delivery: ₹4,800/month pattern detected",
              "description": "CSV analysis shows you order from Swiggy/Zomato every Friday-Sunday evening (7-9 PM). Over 12 weeks, this totals ₹1,200 avg/weekend (₹400/order × 3 orders). Pattern accounts for 31% of total food budget.",
              "actionable_advice": "Reduce to 1-2 weekend orders instead of 3. Prep meals Thursday night or use grocery delivery Friday morning. Cutting 1 order/weekend saves ₹1,600/month (₹19,200/year). Track progress with a meal prep calendar.",
              "impact_amount": 1600.0,
              "priority": "medium",
              "confidence_score": 0.92,
              "valid_until": null,
              "visualization_data": null
            }

            ### ✅ GOOD - Savings Opportunity (Quantified, Actionable):
            {
              "id": "savings_swiggy_alternatives_2025",
              "type": "savings_opportunity",
              "title": "Swiggy alternatives: Save ₹4,500/month",
              "description": "You spent ₹8,450 at Swiggy (15 orders, ₹563 avg/order) this month. CSV shows peak ordering Mon-Thu 8-9 PM (work dinners). Local restaurants like Paradise Biryani, Hotel Empire offer similar meals at ₹280-350. Weekend orders avg ₹650 (premium pricing).",
              "actionable_advice": "Replace 10 weekday Swiggy orders with direct restaurant orders or meal prep (saves ₹2,500). Keep 5 weekend Swiggy for convenience. Subscribe to local tiffin service (₹3,500/month for 20 meals vs ₹8,450). Total potential savings: ₹4,500-5,000/month (₹60,000/year).",
              "impact_amount": 4500.0,
              "priority": "high",
              "confidence_score": 0.88,
              "valid_until": null,
              "visualization_data": null
            }

            ### ✅ GOOD - Top Merchant Savings Analysis:
            {
              "id": "merchant_hungerbox_optimization_2025",
              "type": "savings_opportunity",
              "title": "HungerBox lunch: ₹2,700/month - Save ₹1,500 with tiffin",
              "description": "HungerBox is your #1 merchant: 18 transactions, ₹2,700 total, ₹150 avg/meal. CSV shows daily office lunch pattern (Mon-Fri, 1-2 PM). You're spending ₹3,000/month on workday lunches (20 working days × ₹150). Local tiffin services deliver office lunches at ₹80-100/meal.",
              "actionable_advice": "Subscribe to monthly tiffin service: ₹1,800-2,000 for 20 meals (vs current ₹3,000). Keep HungerBox for occasional variety (3-4 times/month = ₹600). Net monthly cost: ₹2,400-2,600 vs ₹3,000. Savings: ₹400-600/month (₹7,200/year). Try services: DabbaDrop, LunchBox, Office Tiffin.",
              "impact_amount": 500.0,
              "priority": "high",
              "confidence_score": 0.90,
              "valid_until": null,
              "visualization_data": null
            }

            ### ❌ BAD - Generic, No Specific Data:
            {
              "id": "generic_savings",
              "type": "savings_opportunity",
              "title": "Try to save on food expenses",
              "description": "You spend a lot on food delivery. This could be reduced.",
              "actionable_advice": "Cook at home more often to save money.",
              "impact_amount": 0.0,  // ❌ No quantified savings
              "priority": "medium",
              "confidence_score": 0.50,
              "valid_until": null,
              "visualization_data": null
            }

            ## What Makes a HIGH-QUALITY Insight:
            ✅ **Specific**: References actual merchants, amounts, days, times from data
            ✅ **Actionable**: Provides concrete steps with named alternatives
            ✅ **Quantified**: Includes exact ₹ savings calculations with annual projection
            ✅ **Evidence-based**: Derived from CSV patterns, not assumptions
            ✅ **Contextual**: Considers user preferences, previous periods, Indian context
            ✅ **Realistic**: Achievable recommendations, not drastic lifestyle changes

            ## What to AVOID:
            ❌ Generic advice: "try to save money", "reduce spending"
            ❌ Insights without numbers: "you spend too much"
            ❌ Vague recommendations: "cook at home more"
            ❌ Data-free claims: not backed by provided data
            ❌ Duplicate insights: saying same thing differently
            ❌ Unrealistic advice: "stop all food delivery" (too drastic)

            ## Response Checklist (Verify Before Returning):
            ☑ Contains 6-8 insights minimum
            ☑ At least 2 insights are type "savings_opportunity"
            ☑ Top 3-5 merchants are individually analyzed
            ☑ Every savings insight has specific ₹ amount
            ☑ All insights reference actual data (merchants, amounts, days, times)
            ☑ Actionable advice includes specific steps and alternatives
            ☑ Valid JSON format (no syntax errors)
            ☑ All amounts use ₹ symbol
            ☑ No generic/vague advice

            Remember: Your PRIMARY goal is helping users identify WHERE and HOW they can save money based on ACTUAL spending patterns in their data. Be specific, quantify everything, and provide actionable alternatives with exact ₹ savings.
            """;
    }
}
```

---

## 🔧 Configuration Classes

### **AzureOpenAIConfig.java**

```java
package com.flying.solo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "azure.openai")
@Data
public class AzureOpenAIConfig {
    private String endpoint;
    private String key;
    private String deploymentName;
}
```

### **application.yml**

```yaml
azure:
  openai:
    endpoint: https://your-resource.openai.azure.com/openai/deployments/your-deployment/chat/completions?api-version=2024-02-15-preview
    key: ${AZURE_OPENAI_KEY}
    deployment-name: gpt-4

spring:
  application:
    name: expense-insights-service
```

---

## 📊 Expected Request/Response

### **Request from Android App:**

```json
{
  "user_id": "expense_manager_user_test",
  "timeframe": "last_30_days",
  "transaction_summary": {
    "total_spent": 6216.0,
    "transaction_count": 8,
    "category_breakdown": [
      {
        "category_name": "Food & Dining",
        "total_amount": 2416.0,
        "transaction_count": 3,
        "percentage": 38.86,
        "average_per_transaction": 805.33
      }
    ],
    "top_merchants": [
      {
        "merchant_name": "HungerBox",
        "total_amount": 1350.0,
        "transaction_count": 9,
        "category_name": "Food & Dining",
        "average_amount": 150.0
      },
      {
        "merchant_name": "SWIGGY",
        "total_amount": 1066.0,
        "transaction_count": 2,
        "category_name": "Food & Dining",
        "average_amount": 533.0
      }
    ],
    "monthly_trends": [...]
  },
  "transactions_csv": "Date,Amount,Merchant,Category,Type,Bank,DayOfWeek,TimeOfDay\n2025-08-01 14:30,150.0,HungerBox,Food & Dining,Debit,HDFC,Thursday,Afternoon\n...",
  "csv_metadata": {
    "total_transactions": 200,
    "date_range_start": "2025-02-01 10:00:00",
    "date_range_end": "2025-08-01 21:30:00",
    "csv_size_bytes": 18500,
    "includes_categories": true,
    "includes_time_analysis": true
  },
  "prompts": [
    "Generate spending forecast for next month based on current patterns",
    "Identify unusual spending patterns or anomalies",
    "Suggest budget optimization strategies",
    "Find savings opportunities in top spending categories",
    "Analyze merchant spending efficiency"
  ]
}
```

### **Expected AI Response:**

```json
{
  "success": true,
  "insights": [
    {
      "id": "savings_hungerbox_alternative_2025",
      "type": "savings_opportunity",
      "title": "HungerBox lunch: Save ₹1,200/month with tiffin service",
      "description": "HungerBox is your top merchant with 9 transactions totaling ₹1,350 (₹150/meal). CSV shows daily office lunch pattern Mon-Fri 1-2 PM. Monthly projection: ₹3,000 for 20 working days. Local tiffin services offer similar meals at ₹80-100.",
      "actionable_advice": "Subscribe to monthly tiffin service (₹1,800-2,000 for 20 meals vs ₹3,000 current). Keep HungerBox 3-4x/month for variety (₹600). Total monthly cost: ₹2,400-2,600. Savings: ₹400-600/month (₹7,200/year).",
      "impact_amount": 500.0,
      "priority": "high",
      "confidence_score": 0.90,
      "valid_until": null,
      "visualization_data": null
    },
    {
      "id": "savings_swiggy_frequency_2025",
      "type": "savings_opportunity",
      "title": "Reduce Swiggy orders: Save ₹2,000/month",
      "description": "You spent ₹1,066 on 2 Swiggy orders (₹533 avg). This is 70% higher than typical food delivery (₹300-350). CSV shows weekend evening orders (Friday-Saturday 8-9 PM). Projected monthly: ₹4,000-5,000 if pattern continues.",
      "actionable_advice": "Limit Swiggy to 5 orders/month max (₹2,500). For other meals, order directly from restaurants (saves 40%) or meal prep weekends. Potential savings: ₹2,000-2,500/month (₹30,000/year).",
      "impact_amount": 2000.0,
      "priority": "high",
      "confidence_score": 0.85,
      "valid_until": null,
      "visualization_data": null
    },
    {
      "id": "pattern_weekend_spending_2025",
      "type": "pattern_alert",
      "title": "Weekend spending spike: 45% of monthly expenses",
      "description": "CSV analysis reveals Friday-Sunday spending is ₹2,800 (45% of ₹6,216 total). Pattern: Food delivery peaks 7-9 PM weekends. Average ₹700/day weekend vs ₹400/day weekdays.",
      "actionable_advice": "Plan weekend meals in advance. Grocery shop Friday morning, prep meals Saturday. This reduces delivery dependency. Target: Weekend spending ≤ ₹1,500 (saves ₹1,300/month).",
      "impact_amount": 1300.0,
      "priority": "medium",
      "confidence_score": 0.88,
      "valid_until": null,
      "visualization_data": null
    },
    {
      "id": "forecast_september_2025",
      "type": "spending_forecast",
      "title": "September forecast: ₹7,800 (25% increase)",
      "description": "Based on current ₹6,216 in partial month and historical trends, full month projection is ₹7,500-8,000. Food & Dining (39% of budget) is primary driver. If HungerBox pattern continues: ₹3,000 alone.",
      "actionable_advice": "Set monthly budget at ₹7,000. Implement tiffin service and reduce Swiggy orders as suggested. This keeps spending under ₹6,000 (saves ₹1,800-2,000).",
      "impact_amount": 1800.0,
      "priority": "medium",
      "confidence_score": 0.87,
      "valid_until": null,
      "visualization_data": null
    },
    {
      "id": "budget_food_category_2025",
      "type": "budget_optimization",
      "title": "Food & Dining: 39% of budget (recommend 25%)",
      "description": "₹2,416 on food (39% of ₹6,216) exceeds recommended 25% allocation. HungerBox + Swiggy = ₹2,416 total. Ideal food budget: ₹1,550 (25% of ₹6,216).",
      "actionable_advice": "Reduce food delivery expenses by ₹866/month. Implement: Tiffin service (saves ₹500) + Reduce Swiggy orders (saves ₹400). This brings food spending to recommended 25%.",
      "impact_amount": 866.0,
      "priority": "high",
      "confidence_score": 0.91,
      "valid_until": null,
      "visualization_data": null
    }
  ],
  "metadata": {
    "generated_at": 1722531600,
    "model_version": "gpt-4",
    "processing_time_ms": 1750,
    "total_insights": 5
  }
}
```

---

## 🧪 Testing Guide

### **1. Test with cURL:**

```bash
curl -X POST http://localhost:8080/api/ai/insights \
  -H "Content-Type: application/json" \
  -d '{
    "user_id": "test_user",
    "timeframe": "last_30_days",
    "transaction_summary": { ... },
    "transactions_csv": "Date,Amount,Merchant,Category,Type,Bank,DayOfWeek,TimeOfDay\n...",
    "csv_metadata": { ... }
  }'
```

### **2. Expected Logs:**

```
INFO: Processing AI request - Transactions: 200, CSV Size: 18KB
INFO: AI insights generated successfully
```

### **3. Validation Checklist:**

✅ Response contains 5-8 insights
✅ At least 2 insights have `type: "savings_opportunity"`
✅ Top merchants are individually analyzed
✅ Every savings insight has `impact_amount` > 0
✅ Descriptions reference specific merchants, amounts, days
✅ Actionable advice includes specific alternatives
✅ Valid JSON format

---

## 🎯 Key Improvements in This Implementation

| Feature | Benefit |
|---------|---------|
| **MANDATORY Requirements Section** | Forces AI to generate savings insights |
| **Enhanced Top Merchant Prompt** | Explicit instructions for pricing comparison |
| **Concrete Examples** | Shows AI exactly what quality insights look like |
| **Savings-Focused System Prompt** | Primary mission is finding savings opportunities |
| **Quantified Requirements** | "At least 2 savings_opportunity insights" - not optional |
| **Specific Alternative Analysis** | AI must suggest named cheaper alternatives |
| **CSV Pattern Instructions** | Detailed guidance on using CSV for savings detection |

---

## 📝 Implementation Checklist

- [ ] Copy `AIInsightsService.java` to your backend
- [ ] Update `AzureOpenAIConfig.java` with your credentials
- [ ] Configure `application.yml` with Azure OpenAI endpoint
- [ ] Test with sample request
- [ ] Verify response contains `savings_opportunity` insights
- [ ] Check top merchants are individually analyzed
- [ ] Confirm savings amounts are quantified
- [ ] Deploy to production

---

## 🚀 Deployment

1. **Build**: `mvn clean package`
2. **Run**: `java -jar target/expense-insights-service.jar`
3. **Test**: Use Android app to trigger insights
4. **Monitor**: Check logs for CSV processing

---

**Last Updated**: 2025-10-03
**Version**: 2.0 (Complete with Mandatory Savings Insights)
