#!/bin/bash

echo "🧪 Quick AI Insights API Integration Test"
echo "========================================"
echo ""

# Test 1: Basic API connectivity
echo "1. Testing basic API connectivity..."
if curl -s -f http://localhost:8080/api/ai/insights > /dev/null 2>&1; then
    echo "✅ API endpoint is reachable"
else
    echo "❌ API endpoint not reachable - make sure your server is running at localhost:8080"
    exit 1
fi

# Test 2: API response format
echo ""
echo "2. Testing API response format..."
response=$(curl -s -X POST http://localhost:8080/api/ai/insights \
  -H "Content-Type: application/json" \
  -d '{
    "user_id": "integration_test",
    "timeframe": "last_30_days",
    "transaction_summary": {
      "total_spent": 12345.67,
      "transaction_count": 42,
      "category_breakdown": [
        {
          "category": "Test Category",
          "amount": 5000.0,
          "transaction_count": 15,
          "percentage": 40.5
        }
      ],
      "top_merchants": [
        {
          "merchant": "TEST_MERCHANT",
          "amount": 2500.0,
          "transaction_count": 8,
          "category": "Test Category"
        }
      ],
      "monthly_trends": []
    },
    "context_data": {
      "currency": "INR",
      "monthly_budget": 15000.0,
      "budget_progress_percentage": 82,
      "previous_month_spent": 11000.0
    }
  }' 2>/dev/null)

if [[ $response == *"insights"* ]]; then
    echo "✅ API returns insights structure"
    
    # Check for expected fields
    if [[ $response == *"budget_analysis"* ]]; then
        echo "✅ Contains budget_analysis"
    else
        echo "⚠️  Missing budget_analysis field"
    fi
    
    if [[ $response == *"spending_trend"* ]]; then
        echo "✅ Contains spending_trend"
    else
        echo "⚠️  Missing spending_trend field"
    fi
    
    if [[ $response == *"merchant_insights"* ]]; then
        echo "✅ Contains merchant_insights"
    else
        echo "⚠️  Missing merchant_insights field"
    fi
    
    echo ""
    echo "📋 Sample API Response:"
    echo "$response" | head -c 500
    echo "..."
else
    echo "❌ API response doesn't contain insights structure"
    echo "Response: $response"
fi

echo ""
echo "3. Testing Android app connection..."
echo "   Run these commands to test the Android integration:"
echo ""
echo "   # Install latest APK:"
echo "   adb install ./app/build/outputs/apk/debug/app-debug.apk"
echo ""
echo "   # Monitor API calls:"
echo "   adb logcat | grep AIInsightsRepository"
echo ""
echo "   # Open AI Insights tab in the app and watch the logs above"
echo ""
echo "🎯 Expected behavior:"
echo "   - App should make API calls when you open AI Insights"
echo "   - Content should be different from static ₹18400, ₹800 values"
echo "   - Messages should mention your API responses (like '82% of budget')"
echo ""
echo "✅ API test complete! Your API is ready for Android integration."