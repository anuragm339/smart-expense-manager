# Backend Update Instructions - Add Savings Insights

## 🎯 Problem
Your backend AI is not generating:
1. **savings_opportunity** type insights
2. Detailed **top merchant** analysis with alternatives

## ✅ Solution
Update your backend prompt to explicitly require these insights.

---

## 📝 What to Update

### File: `AIInsightsService.java` (or your service class)

### 1. Update the `buildStructuredPrompt()` method:

**Find this section (around Top Merchants):**
```java
// Section 5: Top Merchants
prompt.append("## Top Merchants\n");
for (var merchant : dto.transactionSummary().topMerchants()) {
    prompt.append("- **").append(merchant.merchantName()).append("**: ")
          .append("₹").append(String.format("%.2f", merchant.totalAmount()))
          .append(" (").append(merchant.transactionCount()).append(" visits, ")
          .append("category: ").append(merchant.categoryName()).append(")\n");
}
prompt.append("\n");
```

**Replace with:**
```java
// Section 5: Top Merchants (WITH EXPLICIT SAVINGS ANALYSIS REQUEST)
prompt.append("## Top Merchants (ANALYZE FOR SAVINGS OPPORTUNITIES)\n");
prompt.append("**IMPORTANT**: For each top merchant, identify potential savings by comparing with alternatives.\n\n");
for (var merchant : dto.transactionSummary().topMerchants()) {
    prompt.append("- **").append(merchant.merchantName()).append("**: ")
          .append("₹").append(String.format("%.2f", merchant.totalAmount()))
          .append(" (").append(merchant.transactionCount()).append(" visits, ")
          .append("avg ₹").append(String.format("%.2f", merchant.averageAmount())).append("/visit, ")
          .append("category: ").append(merchant.categoryName()).append(")\n");
}
prompt.append("\n**REQUIRED**: Generate at least ONE 'savings_opportunity' insight analyzing these merchants.\n");
prompt.append("Compare their pricing with typical alternatives (e.g., Swiggy vs local restaurants, premium vs budget options).\n\n");
```

### 2. Add MANDATORY requirements section:

**Find the Instructions section (Section 1):**
```java
// Section 1: Analysis Instructions
prompt.append("# Financial Analysis Request\n\n");
prompt.append("## Instructions\n");
for (String instruction : dto.prompts()) {
    prompt.append("- ").append(instruction).append("\n");
}
prompt.append("\n");
```

**Add this AFTER the instructions:**
```java
prompt.append("\n## MANDATORY Requirements:\n");
prompt.append("1. **MUST include 'savings_opportunity' insights** - Analyze top merchants for cheaper alternatives\n");
prompt.append("2. **MUST analyze top 3-5 merchants individually** - Compare pricing, suggest alternatives\n");
prompt.append("3. **MUST use CSV data** - Identify day/time patterns for targeted savings recommendations\n");
prompt.append("4. **MUST quantify savings** - Every savings insight should include specific ₹ amounts\n\n");
```

### 3. Update System Prompt:

**Find your `buildSystemPrompt()` method and add this section BEFORE "Output Format":**

```java
## MANDATORY Insight Types to Include:
1. **savings_opportunity** (REQUIRED): MUST identify at least 1-2 concrete savings opportunities
   - Analyze top merchants for alternatives/cheaper options
   - Look for subscription overlaps or unused services
   - Identify premium pricing vs budget alternatives
   - Example: "You spend ₹800 avg per visit at Swiggy. Switching to BigBasket for groceries could save ₹5,000/month"

2. **Top Merchant Analysis** (REQUIRED): MUST analyze spending at top 3-5 merchants
   - Compare merchant pricing (is this merchant expensive?)
   - Frequency analysis (visiting too often?)
   - Suggest cheaper alternatives or loyalty programs
   - Example: "HungerBox charges ₹150 avg per meal. Local restaurants offer similar meals at ₹80-100"
```

### 4. Add Example Insights:

**Replace your current example with these THREE examples:**

```java
## Example High-Quality Insights:

### Pattern Alert Example:
{
  "id": "pattern_weekend_food_2024",
  "type": "pattern_alert",
  "title": "High weekend food delivery spending detected",
  "description": "Analysis of 140 transactions over 6 months shows you spend an average of ₹1,200 on Swiggy and Zomato every Friday-Sunday evening (7-9 PM). This pattern accounts for 35% of your total food budget and represents ₹14,400/month on food delivery alone.",
  "actionable_advice": "Consider meal planning on weekends or using grocery delivery services like BigBasket instead. Cooking at home could save approximately ₹7,200/month (50% reduction), totaling ₹86,400/year. Start with one weekend day to build the habit.",
  "impact_amount": 7200.0,
  "priority": "medium",
  "confidence_score": 0.92,
  "valid_until": null,
  "visualization_data": null
}

### Savings Opportunity Example:
{
  "id": "savings_swiggy_alternative_2025",
  "type": "savings_opportunity",
  "title": "Save ₹4,500/month by reducing Swiggy orders",
  "description": "You've spent ₹8,450 at Swiggy (15 orders, ₹563 avg) this month. Analysis shows you order most frequently on weekdays (Mon-Thu) around 8-9 PM, likely due to late work hours. Local restaurants offer similar meals at ₹250-350 per order.",
  "actionable_advice": "Replace 10 Swiggy orders with local restaurant takeout or meal prep. This could save ₹3,000-4,500 per month. Use Swiggy only for weekend treats (max 5 orders). Potential annual savings: ₹54,000.",
  "impact_amount": 4500.0,
  "priority": "high",
  "confidence_score": 0.88,
  "valid_until": null,
  "visualization_data": null
}

### Top Merchant Analysis Example:
{
  "id": "merchant_analysis_hungerbox_2025",
  "type": "savings_opportunity",
  "title": "HungerBox lunch expenses: ₹150/meal - Consider alternatives",
  "description": "HungerBox is your #1 merchant with 18 transactions totaling ₹2,700 (₹150 avg/meal). You order lunch 4-5 times per week, spending ₹12,000 monthly on office lunches. Local tiffin services offer similar meals at ₹80-100.",
  "actionable_advice": "Subscribe to a monthly tiffin service (₹3,000-4,000/month) or pack lunch 2-3 days/week. This could reduce lunch expenses to ₹5,000/month, saving ₹7,000 monthly (₹84,000 annually). Keep HungerBox for occasional convenience.",
  "impact_amount": 7000.0,
  "priority": "high",
  "confidence_score": 0.90,
  "valid_until": null,
  "visualization_data": null
}
```

---

## 🧪 Testing

After making these changes:

1. **Restart your Spring Boot backend**
2. **Trigger AI insights from the Android app**
3. **Verify the response includes:**
   - At least 1-2 `savings_opportunity` insights
   - Specific merchant analysis with alternatives
   - Quantified savings amounts (₹)

## ✨ Expected Improvements

You should now see insights like:
- "Save ₹5,000/month by using tiffin service instead of HungerBox"
- "Swiggy charges ₹563/order. Local restaurants offer same at ₹300 - Save ₹3,900/month"
- "Your top merchant is Swiggy (₹8,450). Consider meal prep to save 60%"

---

## 📚 Reference

See the complete updated implementation in: `BACKEND_AI_INTEGRATION.md`

The key changes ensure the AI:
1. **Knows it MUST generate savings insights** (not optional)
2. **Has clear examples** to follow
3. **Gets explicit merchant comparison instructions**
4. **Receives data in format optimized for savings analysis**
