# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Smart Expense Manager Android app that uses AI to automatically parse bank SMS messages, categorize transactions, and provide financial insights. The project is currently in planning/early development stage with only a comprehensive README.md documenting the intended architecture.

## Development Commands

Since this is a new Android project without existing build files, here are the expected commands once the project is set up:

```bash
# Build the project
./gradlew assembleDebug

# Run tests
./gradlew test

# Run UI tests
./gradlew connectedAndroidTest

# Clean build
./gradlew clean

# Generate debug APK
./gradlew assembleDebug

# Install on device
./gradlew installDebug
```

## Architecture Overview

This Android app follows MVVM (Model-View-ViewModel) architecture with the following key components:

### Core Structure
- **Language**: Kotlin/Java
- **UI Framework**: Material Design Components
- **Database**: Room (SQLite wrapper)
- **Architecture Pattern**: MVVM
- **Navigation**: Fragment-based with bottom navigation

### Key Components to Implement

1. **SMS Processing Engine**
   - `SMSReceiver.kt` - BroadcastReceiver for incoming SMS
   - `TransactionParser.kt` - Parses bank SMS into transaction objects
   - Bank-specific regex patterns for different banks (HDFC, ICICI, SBI)

2. **AI/ML Components**
   - `AIAnalyzer.kt` - Main AI engine for insights
   - `CategoryClassifier.kt` - Merchant-to-category mapping
   - `AIInsightGenerator.kt` - Generates spending insights and recommendations

3. **UI Screens**
   - `DashboardFragment.kt` - Main screen with spending overview
   - `AIInsightsFragment.kt` - AI-powered financial insights
   - `MessagesFragment.kt` - SMS transaction history
   - `CategoriesFragment.kt` - Expense category management
   - `SettingsFragment.kt` - App configuration

4. **Data Layer**
   - `ExpenseDatabase.kt` - Room database setup
   - Transaction, Category, and AIInsight data models
   - Repository pattern for data access

### Required Android Permissions
```xml
<uses-permission android:name="android.permission.READ_SMS" />
<uses-permission android:name="android.permission.RECEIVE_SMS" />
<uses-permission android:name="android.permission.INTERNET" />
```

### Key Dependencies (from README)
- Material Design Components
- Room database with KTX extensions
- Navigation Component
- MPAndroidChart for data visualization
- Dexter for permission handling
- Lifecycle ViewModel

## Development Approach

When implementing features:

1. **SMS Parsing**: Start with basic regex patterns for major Indian banks
2. **Category Classification**: Begin with rule-based merchant mapping, evolve to ML
3. **UI**: Follow Material Design guidelines with the specified color scheme
4. **Privacy**: Keep all data local, no cloud synchronization
5. **Testing**: Use physical devices for SMS testing functionality

## Data Models

Core entities as specified in README:
- `Transaction` - Individual expense records with SMS metadata
- `Category` - Spending categories with aggregated data
- `AIInsight` - Generated financial insights and recommendations

## Security Considerations

- All transaction data stored locally using Room
- Implement data encryption at rest
- Transparent SMS permission handling
- Provide clear privacy policy and data deletion features
- No cloud sync to maintain privacy

## Project Status

This is a planning-stage project with comprehensive documentation but no actual code implementation yet. The README.md contains detailed specifications for building an AI-powered expense management Android app.