# AI Insights Generation Prompt Template

## Master Prompt for AI Financial Analysis

Use this prompt template when calling your AI model (GPT-4, Claude, Gemini, etc.) to generate personalized financial insights:

```
You are an expert financial advisor and data analyst. Analyze the following user's spending data and generate personalized financial insights.

**USER CONTEXT:**
- Currency: {currency}
- Monthly Budget: {monthly_budget}
- Budget Used: {budget_progress_percentage}%
- Days Elapsed This Month: {days_elapsed} out of {days_in_current_month}
- Previous Month Spent: {previous_month_spent}
- Current Month Spent So Far: {total_spent}
- Spending Change: {spending_change_percentage}%

**TRANSACTION SUMMARY:**
Total Transactions: {transaction_count}
Current Month Spending: {total_spent}

**CATEGORY BREAKDOWN:**
{category_breakdown_formatted}

**TOP MERCHANTS:**
{top_merchants_formatted}

**3-MONTH SPENDING TREND:**
{monthly_trends_formatted}

**ANALYSIS INSTRUCTIONS:**

1. **Generate 3-5 insights** from the following types:
   - SPENDING_FORECAST: Predict end-of-month spending based on current patterns
   - PATTERN_ALERT: Identify unusual spending patterns or significant changes
   - SAVINGS_OPPORTUNITY: Find areas where user can reduce expenses
   - MERCHANT_RECOMMENDATION: Analyze merchant spending patterns
   - BUDGET_ALERT: Alert if budget limits are being approached/exceeded

2. **For each insight, provide:**
   - Specific, data-driven analysis
   - Actionable advice with concrete steps
   - Quantified impact amounts (potential savings/overspending)
   - Appropriate priority level (URGENT, HIGH, MEDIUM, LOW)

3. **Insight Guidelines:**
   - Be specific with numbers and percentages
   - Compare to previous months when relevant
   - Focus on actionable recommendations
   - Consider seasonal patterns if apparent
   - Prioritize insights that can save money or prevent overspending

4. **Response Format:**
   Return ONLY a valid JSON response in this exact structure:
   {
     "success": true,
     "insights": [
       {
         "id": "unique_id",
         "type": "INSIGHT_TYPE",
         "title": "Brief insight title",
         "description": "Detailed analysis with specific numbers",
         "actionable_advice": "Specific steps the user can take",
         "impact_amount": numerical_value,
         "priority": "PRIORITY_LEVEL",
         "confidence_score": 0.0_to_1.0,
         "created_at": "current_timestamp",
         "expires_at": "expiry_timestamp",
         "metadata": {
           "key": "value"
         }
       }
     ],
     "summary": {
       "total_insights": number,
       "high_priority_count": number,
       "medium_priority_count": number,
       "total_potential_savings": number,
       "generated_at": "timestamp",
       "cache_duration_hours": 4
     }
  }

**IMPORTANT RULES:**
- All amounts should be in {currency}
- Be precise with calculations
- Only generate insights that are actually supported by the data
- Avoid generic advice - make it specific to this user's patterns
- If budget is exceeded or close to being exceeded, mark as URGENT priority
- Calculate realistic savings amounts based on actual spending patterns

Generate insights now:
```

## Dynamic Prompt Builder Function

Here's a function to automatically build the prompt with real data:

```python
def build_ai_insights_prompt(request_data):
    """
    Build AI prompt using the request data from Android app
    """
    
    # Extract data
    transaction_summary = request_data.get('transaction_summary', {})
    context_data = request_data.get('context_data', {})
    
    # Format category breakdown
    categories = transaction_summary.get('category_breakdown', [])
    category_breakdown = "\n".join([
        f"- {cat['category']}: {context_data.get('currency', 'INR')} {cat['amount']} ({cat['percentage']:.1f}%) - {cat['transaction_count']} transactions"
        for cat in categories
    ])
    
    # Format top merchants
    merchants = transaction_summary.get('top_merchants', [])
    top_merchants = "\n".join([
        f"- {merchant['merchant']}: {context_data.get('currency', 'INR')} {merchant['amount']} ({merchant['transaction_count']} transactions)"
        for merchant in merchants
    ])
    
    # Format monthly trends
    trends = transaction_summary.get('monthly_trends', [])
    monthly_trends = "\n".join([
        f"- {trend['month']}: {context_data.get('currency', 'INR')} {trend['total_amount']} ({trend['transaction_count']} transactions, avg: {trend['average_per_transaction']:.0f})"
        for trend in trends
    ])
    
    # Build the complete prompt
    prompt = f"""
You are an expert financial advisor and data analyst. Analyze the following user's spending data and generate personalized financial insights.

**USER CONTEXT:**
- Currency: {context_data.get('currency', 'INR')}
- Monthly Budget: {context_data.get('currency', 'INR')} {context_data.get('monthly_budget', 'Not set')}
- Budget Used: {context_data.get('budget_progress_percentage', 0)}%
- Days Elapsed This Month: {context_data.get('days_elapsed', 'Unknown')} out of {context_data.get('days_in_current_month', 'Unknown')}
- Previous Month Spent: {context_data.get('currency', 'INR')} {context_data.get('previous_month_spent', 0)}
- Current Month Spent So Far: {context_data.get('currency', 'INR')} {transaction_summary.get('total_spent', 0)}
- Spending Change: {context_data.get('spending_change_percentage', 0)}%

**TRANSACTION SUMMARY:**
Total Transactions: {transaction_summary.get('transaction_count', 0)}
Current Month Spending: {context_data.get('currency', 'INR')} {transaction_summary.get('total_spent', 0)}

**CATEGORY BREAKDOWN:**
{category_breakdown}

**TOP MERCHANTS:**
{top_merchants}

**3-MONTH SPENDING TREND:**
{monthly_trends}

**ANALYSIS INSTRUCTIONS:**

1. **Generate 3-5 insights** from the following types:
   - SPENDING_FORECAST: Predict end-of-month spending based on current patterns
   - PATTERN_ALERT: Identify unusual spending patterns or significant changes
   - SAVINGS_OPPORTUNITY: Find areas where user can reduce expenses
   - MERCHANT_RECOMMENDATION: Analyze merchant spending patterns
   - BUDGET_ALERT: Alert if budget limits are being approached/exceeded

2. **For each insight, provide:**
   - Specific, data-driven analysis
   - Actionable advice with concrete steps
   - Quantified impact amounts (potential savings/overspending)
   - Appropriate priority level (URGENT, HIGH, MEDIUM, LOW)

3. **Insight Guidelines:**
   - Be specific with numbers and percentages
   - Compare to previous months when relevant
   - Focus on actionable recommendations
   - Consider seasonal patterns if apparent
   - Prioritize insights that can save money or prevent overspending

4. **Response Format:**
   Return ONLY a valid JSON response matching the API specification provided.

**IMPORTANT RULES:**
- All amounts should be in {context_data.get('currency', 'INR')}
- Be precise with calculations
- Only generate insights that are actually supported by the data
- Avoid generic advice - make it specific to this user's patterns
- If budget is exceeded or close to being exceeded, mark as URGENT priority
- Calculate realistic savings amounts based on actual spending patterns

Generate insights now:
"""
    
    return prompt
```

## Example API Implementation (Python/Flask)

```python
import openai
import json
from flask import Flask, request, jsonify

app = Flask(__name__)

@app.route('/api/v1/insights/generate', methods=['POST'])
def generate_insights():
    try:
        # Get request data
        request_data = request.get_json()
        
        # Build the prompt
        prompt = build_ai_insights_prompt(request_data)
        
        # Call AI model (OpenAI GPT-4 example)
        response = openai.ChatCompletion.create(
            model="gpt-4",
            messages=[
                {"role": "system", "content": "You are a financial analyst that returns only valid JSON responses."},
                {"role": "user", "content": prompt}
            ],
            temperature=0.3,
            max_tokens=2000
        )
        
        # Parse AI response
        ai_response = response.choices[0].message.content
        insights_data = json.loads(ai_response)
        
        return jsonify(insights_data)
        
    except Exception as e:
        return jsonify({
            "success": false,
            "insights": [],
            "summary": null,
            "error": {
                "code": "PROCESSING_ERROR",
                "message": f"Failed to generate insights: {str(e)}",
                "details": "Please try again later",
                "retry_after": 3600
            }
        }), 500

if __name__ == '__main__':
    app.run(debug=True)
```

## Sample Prompt with Real Data

Here's what the actual prompt would look like with real user data:

```
You are an expert financial advisor and data analyst. Analyze the following user's spending data and generate personalized financial insights.

**USER CONTEXT:**
- Currency: INR
- Monthly Budget: INR 25000
- Budget Used: 98%
- Days Elapsed This Month: 24 out of 31
- Previous Month Spent: INR 26800
- Current Month Spent So Far: INR 24501
- Spending Change: -8.6%

**TRANSACTION SUMMARY:**
Total Transactions: 89
Current Month Spending: INR 24501

**CATEGORY BREAKDOWN:**
- Food & Dining: INR 8500 (34.7%) - 34 transactions
- Shopping: INR 5800 (23.7%) - 12 transactions
- Bills & Utilities: INR 4200 (17.1%) - 8 transactions
- Transportation: INR 3200 (13.1%) - 15 transactions
- Entertainment: INR 2800 (11.4%) - 20 transactions

**TOP MERCHANTS:**
- Swiggy: INR 3420 (23 transactions)
- Big Bazaar: INR 2151 (8 transactions)  
- Uber: INR 1890 (15 transactions)

**3-MONTH SPENDING TREND:**
- 2024-06: INR 22301 (78 transactions, avg: 286)
- 2024-07: INR 26801 (92 transactions, avg: 291)
- 2024-08: INR 24501 (89 transactions, avg: 275)

Generate insights now:
```

## Key Benefits of This Approach

1. **Personalized**: Uses actual user data, not generic advice
2. **Actionable**: Provides specific steps users can take
3. **Quantified**: Includes actual amounts and percentages
4. **Contextual**: Considers budget, trends, and spending patterns
5. **Prioritized**: Helps users focus on most important insights

**This prompt will generate dynamic, personalized insights based on each user's real spending data instead of showing the same hardcoded content to everyone!**