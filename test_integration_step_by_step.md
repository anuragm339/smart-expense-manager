# Step-by-Step Testing Guide: AI Insights Integration

## 🔍 Pre-Test Checklist

### 1. Verify API is Working
```bash
# Test your API directly first
curl -X POST http://localhost:8080/api/ai/insights \
  -H "Content-Type: application/json" \
  -d '{
    "user_id": "test_user",
    "timeframe": "last_30_days",
    "transaction_summary": {
      "total_spent": 15000.0,
      "transaction_count": 50,
      "category_breakdown": [
        {
          "category": "Food & Dining",
          "amount": 6000.0,
          "transaction_count": 20,
          "percentage": 40.0
        }
      ],
      "top_merchants": [
        {
          "merchant": "McDonald",
          "amount": 2000.0,
          "transaction_count": 10,
          "category": "Food & Dining"
        }
      ],
      "monthly_trends": []
    },
    "context_data": {
      "currency": "INR",
      "monthly_budget": 20000.0,
      "budget_progress_percentage": 75,
      "previous_month_spent": 18000.0
    }
  }'
```

**Expected**: Should return insights with different messages mentioning "McDonald", "75%", etc.

### 2. Install Latest APK
```bash
# Uninstall old version first
adb uninstall com.expensemanager.app

# Install fresh APK
adb install ./app/build/outputs/apk/debug/app-debug.apk
```

### 3. Enable Logging
```bash
# Open 3 terminal windows for monitoring:

# Terminal 1: API calls
adb logcat | grep AIInsightsRepository

# Terminal 2: Network activity  
adb logcat | grep NetworkConfig

# Terminal 3: Mapping activity
adb logcat | grep YourApiMapper
```

## 📱 Testing Steps

### Step 1: Launch App and Go to AI Insights
1. Open the app
2. Navigate to "AI Insights" tab
3. **IMMEDIATELY check Terminal 1** - you should see:
   ```
   AIInsightsRepository: Making API call to: AIInsightsApiService
   AIInsightsRepository: Request summary: X spent, Y transactions
   ```

### Step 2: Check What Happens
Watch the logs and the screen simultaneously:

**If Working Correctly:**
- Logs show: `"API call successful: 4 insights received"`
- Screen shows: Content different from the screenshot you sent (not ₹18400, ₹800)
- Messages match your API response (mentions "98% of budget", "Swiggy", etc.)

**If Still Showing Sample Data:**
- Logs show: `"Failed to fetch fresh insights"` or `"Network error"`
- Screen shows: Same static content as before (₹18400, ₹800, 45%)

### Step 3: Force Refresh Test
1. **Pull down** on the AI Insights screen (swipe down gesture)
2. Watch Terminal 1 for new API calls
3. See if content changes

### Step 4: Test Different API Responses
Change your API to return different values and test:

```bash
# Test with different budget status
curl -X POST http://localhost:8080/api/ai/insights -d '{
  "user_id": "test_user_2", 
  "context_data": {"budget_progress_percentage": 150}
}'
```

Then refresh the app and see if it shows "over budget" messages.

## 🔍 Debugging Common Issues

### Issue 1: App Shows Sample Data
**Check logs for:**
- `"No internet connection"`
- `"Connection refused"`
- `"HTTP error"`
- `"Network Error"`

**Solution**: Your API server might not be running or accessible

### Issue 2: API Called But Wrong Content
**Check logs for:**
- `"API returned null response"`
- `"Unknown API error"`

**Solution**: API response format might not match exactly

### Issue 3: No API Calls at All
**Check logs for:**
- `"Using existing cached data"`
- Nothing in AIInsightsRepository logs

**Solution**: App might be using cached data

## 🧪 Quick Verification Tests

### Test 1: Change API Response
Modify your API to return:
```json
{
  "insights": {
    "budget_analysis": {
      "status": "under_budget", 
      "message": "TESTING - You have only spent 50% of budget"
    },
    "merchant_insights": {
      "top_merchant": "TEST_MERCHANT",
      "message": "TESTING - Your top merchant is TEST_MERCHANT"
    }
  }
}
```

Refresh app → Should show "TESTING" messages if integration works.

### Test 2: Stop API Server
1. Stop your API server
2. Clear app cache: `adb shell pm clear com.expensemanager.app`
3. Open AI Insights
4. Should show error state or sample data

### Test 3: Restart API Server  
1. Start API server again
2. Pull to refresh in app
3. Should show live data again

## 📋 Results Interpretation

### ✅ Integration Working If:
- Logs show successful API calls
- Content changes when you modify API responses
- Messages mention actual values from your API (Swiggy, 98%, etc.)
- Pull-to-refresh triggers new API calls

### ❌ Integration NOT Working If:
- Same static content as original screenshot
- No API call logs
- Content never changes regardless of API modifications
- Still shows ₹18400, ₹800, 45% static values

## 🎯 What to Look For

**Dynamic Content Indicators:**
- Messages mention "Swiggy" (from your API)
- Budget percentage shows "98%" (from your API)  
- Spending advice matches your API responses
- Values change when you modify API responses

**Static Content Indicators:**
- Always shows ₹18400 spending forecast
- Always shows ₹800 and ₹9600 savings
- Same 45% food increase message
- Content never changes

## 📊 Test Report Template

After testing, please share:

1. **API Server Status**: Is your API running and responding?
2. **Log Output**: What do you see in `adb logcat | grep AIInsightsRepository`?
3. **Screen Content**: Does the AI Insights screen show different values?
4. **Pull-to-Refresh**: Does swiping down trigger new API calls?
5. **Error Messages**: Any error messages or connection issues?

Run these tests and let me know what you observe - I'll help you debug any issues!