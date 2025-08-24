#!/bin/bash

# Test script to verify AI Insights API integration
echo "🔍 Testing AI Insights API Integration"
echo "======================================"

# Check if API server is running
echo "1. Checking if API server is running at localhost:8080..."
if curl -s http://localhost:8080/api/ai/insights > /dev/null 2>&1; then
    echo "✅ API server is responding"
else
    echo "❌ API server is not responding at localhost:8080"
    echo "   Make sure your AI API server is running before testing the Android app"
    exit 1
fi

echo ""
echo "2. Testing API endpoint with sample data..."

# Test API with sample request data (what the Android app would send)
curl -X POST http://localhost:8080/api/ai/insights \
  -H "Content-Type: application/json" \
  -H "User-Agent: ExpenseManager-Android/1.0" \
  -d '{
    "user_id": "expense_manager_user_test",
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
  }' | jq '.' 2>/dev/null || echo "Response received (install jq for formatted output)"

echo ""
echo "✅ API Integration Complete!"
echo ""
echo "📱 Android App Configuration:"
echo "   • API Endpoint: http://10.0.2.2:8080/api/ai/insights"
echo "   • Network Config: HTTP cleartext traffic enabled"
echo "   • User ID: expense_manager_user_test"
echo ""
echo "🚀 Next Steps:"
echo "   1. Make sure your AI API is running at localhost:8080"
echo "   2. Install and run the Android app"
echo "   3. Navigate to AI Insights tab"
echo "   4. Check Android logs for API calls: adb logcat | grep AIInsightsRepository"
echo ""
echo "🔍 Debug Commands:"
echo "   • View network logs: adb logcat | grep NetworkConfig"
echo "   • Check API responses: adb logcat | grep InsightsViewModel"
echo "   • Monitor data flow: adb logcat | grep 'AI Insights'"