# AI Insights API Specification

## API Endpoint
```
POST https://your-ai-api.com/api/v1/insights/generate
Content-Type: application/json
```

## Request Format

The Android app sends the following data to your AI API:

```json
{
  "user_id": "user_12345",
  "timeframe": "last_30_days",
  "transaction_summary": {
    "total_spent": 24500.75,
    "transaction_count": 89,
    "category_breakdown": [
      {
        "category": "Food & Dining",
        "amount": 8500.50,
        "transaction_count": 34,
        "percentage": 34.69
      },
      {
        "category": "Transportation",
        "amount": 3200.25,
        "transaction_count": 15,
        "percentage": 13.06
      },
      {
        "category": "Shopping",
        "amount": 5800.00,
        "transaction_count": 12,
        "percentage": 23.67
      },
      {
        "category": "Bills & Utilities",
        "amount": 4200.00,
        "transaction_count": 8,
        "percentage": 17.14
      },
      {
        "category": "Entertainment",
        "amount": 2800.00,
        "transaction_count": 20,
        "percentage": 11.43
      }
    ],
    "top_merchants": [
      {
        "merchant": "Swiggy",
        "amount": 3420.50,
        "transaction_count": 23,
        "category": "Food & Dining"
      },
      {
        "merchant": "Big Bazaar",
        "amount": 2150.75,
        "transaction_count": 8,
        "category": "Shopping"
      },
      {
        "merchant": "Uber",
        "amount": 1890.25,
        "transaction_count": 15,
        "category": "Transportation"
      }
    ],
    "monthly_trends": [
      {
        "month": "2024-06",
        "total_amount": 22300.50,
        "transaction_count": 78,
        "average_per_transaction": 285.90
      },
      {
        "month": "2024-07",
        "total_amount": 26800.75,
        "transaction_count": 92,
        "average_per_transaction": 291.31
      },
      {
        "month": "2024-08",
        "total_amount": 24500.75,
        "transaction_count": 89,
        "average_per_transaction": 275.29
      }
    ]
  },
  "context_data": {
    "currency": "INR",
    "monthly_budget": 25000.0,
    "budget_progress_percentage": 98,
    "previous_month_spent": 26800.75,
    "spending_change_percentage": -8.58,
    "days_in_current_month": 31,
    "days_elapsed": 24
  }
}
```

## Expected Response Format

Your AI API should respond with this JSON structure:

```json
{
  "success": true,
  "insights": [
    {
      "id": "forecast_aug_2024",
      "type": "SPENDING_FORECAST",
      "title": "August Spending Projection",
      "description": "Based on your current spending pattern (₹24,501 in 24 days), you're likely to spend ₹31,600 this month.",
      "actionable_advice": "You're on track to exceed your ₹25,000 budget by ₹6,600. Consider reducing food delivery orders to stay within budget.",
      "impact_amount": 31600.0,
      "priority": "HIGH",
      "confidence_score": 0.85,
      "created_at": "2024-08-24T10:30:00Z",
      "expires_at": "2024-08-25T10:30:00Z",
      "metadata": {
        "projected_overspend": 6600.0,
        "days_remaining": 7,
        "daily_budget_remaining": 65.0
      }
    },
    {
      "id": "pattern_food_spike_aug2024",
      "type": "PATTERN_ALERT",
      "title": "Food Expenses Spike Detected",
      "description": "Your food delivery spending increased by 45% compared to last month (₹3,421 vs ₹2,359).",
      "actionable_advice": "Try cooking at home 3 more days per week. This could save you approximately ₹1,200/month.",
      "impact_amount": 1200.0,
      "priority": "MEDIUM",
      "confidence_score": 0.92,
      "created_at": "2024-08-24T10:30:00Z",
      "expires_at": "2024-08-31T23:59:59Z",
      "metadata": {
        "category": "Food & Dining",
        "change_percentage": 45.0,
        "comparison_period": "last_month",
        "trigger_merchant": "Swiggy"
      }
    },
    {
      "id": "savings_transport_aug2024",
      "type": "SAVINGS_OPPORTUNITY",
      "title": "Transportation Savings Available",
      "description": "You spent ₹1,890 on ride-sharing this month. Public transport could reduce this significantly.",
      "actionable_advice": "Switch to metro/bus for regular commutes. Potential savings: ₹900/month (₹10,800/year).",
      "impact_amount": 900.0,
      "priority": "MEDIUM",
      "confidence_score": 0.78,
      "created_at": "2024-08-24T10:30:00Z",
      "expires_at": "2024-09-24T10:30:00Z",
      "metadata": {
        "category": "Transportation",
        "current_spend": 1890.25,
        "estimated_public_transport_cost": 990.0,
        "yearly_savings_potential": 10800.0
      }
    },
    {
      "id": "merchant_alert_bigbazaar_aug2024",
      "type": "MERCHANT_RECOMMENDATION",
      "title": "Shopping Pattern Analysis",
      "description": "You've made 8 transactions at Big Bazaar totaling ₹2,151. Consider bulk shopping to reduce frequency.",
      "actionable_advice": "Plan monthly grocery shopping trips instead of weekly visits to save on impulse purchases.",
      "impact_amount": 400.0,
      "priority": "LOW",
      "confidence_score": 0.65,
      "created_at": "2024-08-24T10:30:00Z",
      "expires_at": "2024-09-07T23:59:59Z",
      "metadata": {
        "merchant": "Big Bazaar",
        "transaction_frequency": "weekly",
        "recommended_frequency": "monthly",
        "impulse_purchase_estimate": 400.0
      }
    },
    {
      "id": "budget_alert_aug2024",
      "type": "BUDGET_ALERT",
      "title": "Budget Limit Approaching",
      "description": "You've used 98% of your ₹25,000 monthly budget with 7 days remaining.",
      "actionable_advice": "Daily spending limit for remaining days: ₹65. Focus on essential expenses only.",
      "impact_amount": 500.0,
      "priority": "URGENT",
      "confidence_score": 1.0,
      "created_at": "2024-08-24T10:30:00Z",
      "expires_at": "2024-08-31T23:59:59Z",
      "metadata": {
        "budget_used_percentage": 98.0,
        "remaining_budget": 500.0,
        "days_remaining": 7,
        "recommended_daily_limit": 65.0
      }
    }
  ],
  "summary": {
    "total_insights": 5,
    "high_priority_count": 1,
    "medium_priority_count": 2,
    "total_potential_savings": 2500.0,
    "generated_at": "2024-08-24T10:30:00Z",
    "cache_duration_hours": 4
  },
  "error": null
}
```

## Error Response Format

If the API fails, return this format:

```json
{
  "success": false,
  "insights": [],
  "summary": null,
  "error": {
    "code": "PROCESSING_ERROR",
    "message": "Unable to generate insights due to insufficient data",
    "details": "Minimum 10 transactions required for meaningful analysis",
    "retry_after": 3600
  }
}
```

## Insight Types Supported

- **SPENDING_FORECAST**: Monthly spending predictions
- **PATTERN_ALERT**: Unusual spending pattern detection
- **SAVINGS_OPPORTUNITY**: Money-saving recommendations
- **MERCHANT_RECOMMENDATION**: Merchant-specific insights
- **BUDGET_ALERT**: Budget-related warnings

## Priority Levels

- **URGENT**: Requires immediate attention (budget exceeded, unusual activity)
- **HIGH**: Important insights (overspending patterns, significant increases)
- **MEDIUM**: Helpful recommendations (savings opportunities)
- **LOW**: General observations (merchant patterns, minor trends)

## Testing Your API

You can test your API using this curl command:

```bash
curl -X POST https://your-ai-api.com/api/v1/insights/generate \
  -H "Content-Type: application/json" \
  -d '{
    "user_id": "test_user",
    "timeframe": "last_30_days",
    "transaction_summary": {
      "total_spent": 24500.75,
      "transaction_count": 89,
      "category_breakdown": [
        {
          "category": "Food & Dining",
          "amount": 8500.50,
          "transaction_count": 34,
          "percentage": 34.69
        }
      ],
      "top_merchants": [
        {
          "merchant": "Swiggy",
          "amount": 3420.50,
          "transaction_count": 23,
          "category": "Food & Dining"
        }
      ],
      "monthly_trends": [
        {
          "month": "2024-08",
          "total_amount": 24500.75,
          "transaction_count": 89,
          "average_per_transaction": 275.29
        }
      ]
    },
    "context_data": {
      "currency": "INR",
      "monthly_budget": 25000.0,
      "budget_progress_percentage": 98,
      "previous_month_spent": 26800.75
    }
  }'
```

## Android App Integration

Once your API is ready:

1. Update `NetworkConfig.kt` with your API base URL
2. The app will automatically start calling your API instead of using sample data
3. All caching, offline support, and error handling will work seamlessly

## Notes for AI Processing

- **Real Data**: The request contains actual user transaction data
- **Context Aware**: Includes budget, previous month comparisons, and trends
- **Personalized**: Each insight should be specific to the user's spending patterns
- **Actionable**: Every insight should include specific, actionable advice
- **Prioritized**: Use priority levels to help users focus on important insights first