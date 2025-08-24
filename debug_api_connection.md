# Debug AI Insights API Connection

## Step 1: Check if API Server is Running

```bash
# Test if your API server is running
curl -X POST http://localhost:8080/api/ai/insights \
  -H "Content-Type: application/json" \
  -d '{"test": "data"}'
```

**Expected**: Should return some response (even if it's an error about the data format)
**If fails**: Your API server isn't running at localhost:8080

## Step 2: Check Android App Logs

Connect your Android device/emulator and run:

```bash
# Check for API call attempts
adb logcat | grep AIInsightsRepository

# Check for network errors
adb logcat | grep NetworkConfig

# Check for any AI Insights related logs
adb logcat | grep "AI Insights"
```

**Look for these log messages:**
- ✅ "Making API call to: AIInsightsApiService"
- ✅ "Request summary: X spent, Y transactions"
- ❌ "Failed to fetch fresh insights"
- ❌ "Network error" or "Connection refused"

## Step 3: Test Network Connectivity from Android

The Android emulator uses `10.0.2.2` to access `localhost` on your computer.

**From your computer, test:**
```bash
# Test if the endpoint is reachable
curl -v http://localhost:8080/api/ai/insights
```

## Step 4: Force API Call from App

In the AI Insights screen:
1. **Pull down to refresh** (swipe down gesture)
2. **Check logs immediately** with: `adb logcat | grep AIInsightsRepository`

## Step 5: Check Current App Behavior

Based on the screenshot showing static data, the app is likely:
1. Trying to call the API
2. Getting an error (network timeout, connection refused, etc.)
3. Falling back to sample data as designed

## Common Issues & Solutions

### Issue 1: API Server Not Running
**Solution**: Start your AI API server at localhost:8080

### Issue 2: Wrong Endpoint
**Check**: Your API should handle `POST /api/ai/insights`
**Current Android config**: `http://10.0.2.2:8080/api/ai/insights`

### Issue 3: CORS or Network Issues
**Android logs will show**: "Network error", "Connection refused", "Timeout"

### Issue 4: API Returns Wrong Format
**Android logs will show**: "API returned error", "Unknown API error"

## Quick Test Commands

```bash
# 1. Check if port 8080 is open
lsof -i :8080

# 2. Test API endpoint exists
curl -I http://localhost:8080/api/ai/insights

# 3. Monitor Android network calls in real-time
adb logcat | grep -E "(AIInsightsRepository|NetworkConfig|Retrofit)"

# 4. Check Android app is making network requests
adb logcat | grep "Making API call"
```

## Expected Behavior When Working

When the API integration works, you should see:

1. **In Android logs:**
   ```
   AIInsightsRepository: Making API call to: AIInsightsApiService
   AIInsightsRepository: Request summary: 24500.75 spent, 89 transactions
   AIInsightsRepository: API call successful: 5 insights received
   ```

2. **In the app:**
   - Different amounts/percentages each time you refresh
   - Insights that reference your actual transaction data
   - Dynamic recommendations based on real spending patterns

## Next Steps

1. **Check your API server status**
2. **Run the debug commands above**
3. **Share the Android log output** so I can see exactly what's happening
4. **Verify your API endpoint format** matches the expected request/response structure