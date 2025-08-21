# Budget Alert Testing Guide

## 🧪 How to Test Budget Alerts

### **Step 1: Install and Launch App**
1. Install the APK on your device
2. Grant SMS permissions when prompted
3. Navigate to **Profile** → **Settings** → **Budget Goals**

### **Step 2: Set Test Budget**
1. Click **"Edit"** button next to Monthly Budget
2. Set a **LOW budget** for testing:
   - If you have real SMS transactions: Set budget to ₹500-1000
   - If no real transactions: Use default ₹15,000 and manually test

### **Step 3: Test Alert Scenarios**

#### **Scenario A: 90% Budget Alert**
- **Expected Trigger**: When you've spent 90%+ of budget
- **Expected Alert**: "💡 Budget Alert" dialog
- **Message**: "You've used XX% of your budget with only ₹XX remaining"
- **Actions**: "View Categories" and "OK" buttons

#### **Scenario B: 100% Over Budget Alert**  
- **Expected Trigger**: When spending exceeds budget
- **Expected Alert**: "🚨 Budget Exceeded!" dialog
- **Message**: "You've exceeded your monthly budget by ₹XX"
- **Actions**: "View Breakdown", "AI Help", "Dismiss" buttons

#### **Scenario C: Budget Insights**
- **Location**: Budget Insights card at bottom of screen
- **Expected Messages**:
  - 🚨 ALERT: Over budget message
  - ⚠️ WARNING: Spending faster than expected
  - ✅ ON TRACK: Normal spending pace
  - 🎯 EXCELLENT: Under budget pace

### **Step 4: Test Budget Recommendations**
1. Trigger over-budget alert
2. Click **"AI Help"** button
3. **Expected**: Recommendations dialog with:
   - Spending analysis
   - Specific savings suggestions
   - "Set Reminders" option

### **Step 5: Test Real vs Projected Spending**
- **Check Budget Status**: Should show "XX% through budget with XX days remaining"
- **Verify Projections**: Should calculate monthly projection based on current pace
- **Month Progress**: Should compare spending progress to calendar progress

## 🔧 Manual Testing Commands

### **Force Different Budget States:**

#### **Test 1: Force Over-Budget State**
```
1. Set Monthly Budget: ₹1000
2. If real SMS total > ₹1000 → Should trigger over-budget alert
3. Check Budget Insights for "🚨 ALERT" message
```

#### **Test 2: Force 90% Alert**  
```
1. Set Monthly Budget: ₹1200
2. If real SMS total ≈ ₹1080 → Should trigger 90% alert
3. Check for "💡 Budget Alert" popup
```

#### **Test 3: Test Category Budgets**
```
1. Add category budgets (Food: ₹500, Transport: ₹300)
2. Check if category progress bars show correctly
3. Verify category over-budget indicators
```

## 📱 Expected UI Behavior

### **Budget Progress Bar:**
- **Green**: Under 70% budget usage
- **Orange**: 70-90% budget usage  
- **Red**: Over 90% budget usage

### **Budget Status Text:**
- Shows current percentage and days remaining
- Compares to month progress percentage
- Shows projected end-of-month spending

### **Category Progress:**
- Individual progress bars per category
- "XX% used" and "₹XX left" or "₹XX over" text
- Color coding based on budget status

## 🐛 Expected Issues to Check

### **Alert Frequency:**
- Alerts should not spam (shown once per threshold per month)
- Check `budget_alerts_shown` in SharedPreferences

### **Data Accuracy:**
- Verify budget calculations match real SMS totals
- Check category spending sums correctly
- Confirm filtering by inclusion state works

### **UI Updates:**
- Budget amounts should update immediately after editing
- Progress bars should reflect real data
- Insights should recalculate on data changes

## 🎯 Success Criteria

✅ **Budget alerts trigger at correct thresholds**  
✅ **Alert messages are relevant and helpful**  
✅ **Real SMS data integrates correctly**  
✅ **Category budgets show actual spending**  
✅ **Budget insights provide actionable advice**  
✅ **UI updates immediately after budget changes**  
✅ **No app crashes or data corruption**

## 📊 Testing Results Template

```
Test Date: ___________
Device: ___________
SMS Transactions Found: ___________

[ ] Budget Alert (90%) - Triggered correctly
[ ] Over Budget Alert (100%+) - Triggered correctly  
[ ] Budget Insights - Showing relevant advice
[ ] Category Budgets - Accurate spending data
[ ] Real-time Updates - Budget changes reflect immediately
[ ] No Crashes - App stable throughout testing

Notes:
___________
___________
```

Start with Step 1 and work through each scenario! 🚀