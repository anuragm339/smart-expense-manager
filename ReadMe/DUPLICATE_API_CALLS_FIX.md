# Duplicate API Calls & Timeout Fix

## 🔴 Problems Identified

### Issue 1: Duplicate Simultaneous API Calls
**Evidence from logs:**
```
17:47:26 --> POST (First API call starts)
17:47:26 --> POST (Second API call starts - DUPLICATE!)
17:48:20 <-- 200 (First call succeeds after 54s)
17:48:27 <-- HTTP FAILED: timeout (Second call times out after 60s)
```

**Root Causes:**
1. `InsightsViewModel.init{}` calls `loadInsights()` automatically
2. Fragment likely triggers another refresh on appear
3. Both start at same time → duplicate API calls

### Issue 2: Timeout Too Short
**Current:** `READ_TIMEOUT = 60L` seconds
**Needed:** `360L` seconds (6 minutes for o1-mini)

o1-mini reasoning model takes 2-5 minutes, so 60-second timeout causes failures.

---

## ✅ Fixes Applied

### Fix 1: Increased Timeout (DONE ✅)

**File:** `NetworkConfig.kt` line 26

**Before:**
```kotlin
private const val READ_TIMEOUT = 60L
```

**After:**
```kotlin
private const val READ_TIMEOUT = 360L  // 6 minutes for o1-mini reasoning model
```

---

### Fix 2: Prevent Duplicate Calls (NEEDS IMPLEMENTATION)

The duplicate calls happen because both `init{}` and Fragment trigger loading simultaneously.

#### Option A: Add Call Deduplication (RECOMMENDED) ⭐

Add a flag to prevent concurrent API calls:

```kotlin
class InsightsViewModel @Inject constructor(
    private val getAIInsightsUseCase: GetAIInsightsUseCase
) : ViewModel() {

    companion object {
        private const val TAG = "InsightsViewModel"
    }

    private val _uiState = MutableStateFlow(InsightsUIState())
    val uiState: StateFlow<InsightsUIState> = _uiState.asStateFlow()

    // ✅ ADD THIS: Track if API call is in progress
    private var isApiCallInProgress = AtomicBoolean(false)

    init {
        Log.d(TAG, "ViewModel initialized, loading insights...")
        loadInsights()
    }

    private fun loadInsights() {
        // ✅ ADD THIS: Check if call already in progress
        if (!isApiCallInProgress.compareAndSet(false, true)) {
            Log.d(TAG, "API call already in progress, skipping duplicate request")
            return
        }

        Log.d(TAG, "Starting initial load...")

        _uiState.value = _uiState.value.copy(
            isInitialLoading = true,
            hasError = false,
            error = null
        )

        viewModelScope.launch {
            try {
                getAIInsightsUseCase.execute().collect { result ->
                    // ... existing handling
                }
            } finally {
                // ✅ ADD THIS: Reset flag when done
                isApiCallInProgress.set(false)
            }
        }
    }

    private fun refreshInsights() {
        // ✅ ADD THIS: Check if call already in progress
        if (!isApiCallInProgress.compareAndSet(false, true)) {
            Log.d(TAG, "API call already in progress, skipping duplicate refresh")
            return
        }

        Log.d(TAG, "Refreshing insights...")

        _uiState.value = _uiState.value.copy(
            isRefreshing = true,
            hasError = false,
            error = null
        )

        viewModelScope.launch {
            try {
                val result = getAIInsightsUseCase.refreshInsights()
                handleInsightsResult(result, isRefresh = true)
            } finally {
                // ✅ ADD THIS: Reset flag when done
                isApiCallInProgress.set(false)
            }
        }
    }
}
```

Add import:
```kotlin
import java.util.concurrent.atomic.AtomicBoolean
```

#### Option B: Use Mutex (Alternative)

```kotlin
class InsightsViewModel @Inject constructor(
    private val getAIInsightsUseCase: GetAIInsightsUseCase
) : ViewModel() {

    // ✅ ADD THIS: Mutex to prevent concurrent calls
    private val refreshMutex = Mutex()

    private fun loadInsights() {
        viewModelScope.launch {
            // ✅ ADD THIS: Lock to prevent concurrent calls
            refreshMutex.withLock {
                Log.d(TAG, "Starting initial load (with mutex lock)...")

                _uiState.value = _uiState.value.copy(
                    isInitialLoading = true,
                    hasError = false,
                    error = null
                )

                getAIInsightsUseCase.execute().collect { result ->
                    // ... existing handling
                }
            }
        }
    }

    private fun refreshInsights() {
        viewModelScope.launch {
            // ✅ ADD THIS: Try to acquire lock, skip if already locked
            if (refreshMutex.tryLock()) {
                try {
                    Log.d(TAG, "Refreshing insights (with mutex lock)...")

                    _uiState.value = _uiState.value.copy(
                        isRefreshing = true,
                        hasError = false,
                        error = null
                    )

                    val result = getAIInsightsUseCase.refreshInsights()
                    handleInsightsResult(result, isRefresh = true)
                } finally {
                    refreshMutex.unlock()
                }
            } else {
                Log.d(TAG, "Refresh already in progress, skipping")
            }
        }
    }
}
```

Add imports:
```kotlin
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
```

---

## 📊 Analysis of Logs

### Timeline Breakdown:

```
17:47:26.478  DATABASE: Dashboard data loaded (₹711,253.53)
17:47:26.525  NetworkConfig: --> POST (First API call)
17:47:26.599  NetworkConfig: --> POST (Second API call - DUPLICATE!)
             ↓
             (Both calls processing in parallel)
             ↓
17:48:20.821  NetworkConfig: <-- 200 (First call succeeds - 54s)
17:48:20.849  AIInsightsRepository: API call successful: 6 insights
17:48:20.920  InsightsViewModel: Insights loaded successfully: 6
             ↓
17:48:27.278  NetworkConfig: <-- HTTP FAILED (Second call times out - 60s)
17:48:27.289  AIInsightsRepository: Exception during API call
17:48:27.291  InsightsViewModel: Insights loaded successfully: 3 (fallback)
```

**What Happened:**
1. ViewModel `init{}` triggered first API call at 17:47:26
2. Something else (Fragment? SwipeRefresh?) triggered second call at 17:47:26 (same second!)
3. First call succeeded after 54s
4. Second call timed out after 60s (too short timeout)
5. App fell back to cached data (3 insights)

---

## 🧪 Testing the Fixes

### Test 1: Verify No Duplicate Calls

**Before fix:**
```
17:47:26 --> POST (call 1)
17:47:26 --> POST (call 2 - duplicate!)
```

**After fix (with AtomicBoolean):**
```
17:47:26 --> POST (call 1)
17:47:26 API call already in progress, skipping duplicate request
```

### Test 2: Verify Timeout Increased

**Before fix:**
```
<-- HTTP FAILED: timeout (after 60s)
```

**After fix:**
```
<-- 200 (success after 54-180s, within 360s timeout)
```

---

## 📋 Implementation Checklist

### Immediate Fixes (DONE ✅):
- [x] Increase `READ_TIMEOUT` from 60s to 360s in `NetworkConfig.kt`

### Recommended Fixes (TODO):
- [ ] Add `AtomicBoolean` flag to `InsightsViewModel.kt`
- [ ] Check flag in `loadInsights()` before API call
- [ ] Check flag in `refreshInsights()` before API call
- [ ] Reset flag in `finally{}` blocks
- [ ] Add logging for skipped duplicate calls
- [ ] Test on device to verify single API call

### Alternative (if AtomicBoolean doesn't work):
- [ ] Use `Mutex` with `tryLock()` instead
- [ ] Add `refreshMutex.withLock{}` in coroutine
- [ ] Test mutex locking behavior

---

## 🎯 Expected Results After Fix

### Logs should show:
```
17:47:26  ViewModel initialized, loading insights...
17:47:26  Starting initial load...
17:47:26  --> POST (single API call)
17:47:26  API call already in progress, skipping duplicate request
          (no second POST!)
17:48:20  <-- 200 (success after 54s)
17:48:20  API call successful: 6 insights
```

### Benefits:
- ✅ Only one API call instead of two
- ✅ No wasted API costs
- ✅ No timeout errors from second call
- ✅ Faster UX (no duplicate processing)
- ✅ Cleaner logs

---

## 🚨 Root Cause Investigation

### Why Two Calls Happen:

**Call 1:** `InsightsViewModel.init{}` → `loadInsights()`
- Happens when ViewModel is created
- Automatically loads data

**Call 2:** Likely one of these triggers:
1. **Fragment `onViewCreated()`** calling `viewModel.refreshInsights()`
2. **SwipeRefreshLayout** auto-triggering on appear
3. **Navigation** back to fragment triggering reload
4. **Lifecycle** event (onStart/onResume) triggering refresh

**Solution:** Deduplication prevents both from executing simultaneously.

---

## 💡 Additional Optimizations

### 1. Debounce Refresh Requests

Add debouncing to prevent rapid successive refreshes:

```kotlin
private var lastRefreshTime = 0L
private val MIN_REFRESH_INTERVAL = 5000L // 5 seconds

private fun refreshInsights() {
    val now = System.currentTimeMillis()
    if (now - lastRefreshTime < MIN_REFRESH_INTERVAL) {
        Log.d(TAG, "Refresh throttled (too soon after last refresh)")
        return
    }
    lastRefreshTime = now

    // ... existing refresh logic
}
```

### 2. Cancel Previous Request

Use `Job` to cancel previous request when new one starts:

```kotlin
private var refreshJob: Job? = null

private fun refreshInsights() {
    // Cancel previous refresh if still running
    refreshJob?.cancel()

    refreshJob = viewModelScope.launch {
        // ... refresh logic
    }
}
```

---

## 📝 Summary

**Problems:**
1. ✅ **Timeout too short**: 60s → 360s (FIXED)
2. ⚠️ **Duplicate API calls**: Need deduplication (TODO)

**Solutions:**
1. ✅ Increased timeout to 6 minutes in `NetworkConfig.kt`
2. 📋 Add `AtomicBoolean` flag to prevent concurrent calls
3. 📋 Check flag before starting API calls
4. 📋 Reset flag when call completes (success or failure)

**Impact:**
- No more timeout errors
- No more duplicate API calls
- Lower API costs
- Better UX

**Files to Update:**
- ✅ `NetworkConfig.kt` (timeout increased)
- 📋 `InsightsViewModel.kt` (add deduplication)

Implement the `AtomicBoolean` fix to eliminate duplicate calls!
