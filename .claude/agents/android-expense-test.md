---
name: android-expense-test
description: # Android Testing Agent for Java/Kotlin Projects\n\n## Overview\nThis guide will help you create a comprehensive testing agent that can generate both unit tests and integration tests for your Android application.\n\n## Project Structure Setup\n\n### 1. Dependencies (build.gradle.kts - app module)\n```kotlin\ndependencies {\n    // Testing frameworks\n    testImplementation("junit:junit:4.13.2")\n    testImplementation("org.mockito:mockito-core:5.1.1")\n    testImplementation("org.mockito:mockito-inline:5.1.1")\n    testImplementation("org.mockito.kotlin:mockito-kotlin:4.1.0")\n    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")\n    testImplementation("androidx.arch.core:core-testing:2.2.0")\n    \n    // Android testing\n    androidTestImplementation("androidx.test.ext:junit:1.1.5")\n    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")\n    androidTestImplementation("androidx.test.espresso:espresso-contrib:3.5.1")\n    androidTestImplementation("androidx.test:runner:1.5.2")\n    androidTestImplementation("androidx.test:rules:1.5.0")\n    androidTestImplementation("org.mockito:mockito-android:5.1.1")\n    \n    // Room testing\n    testImplementation("androidx.room:room-testing:2.5.0")\n    \n    // Hilt testing\n    androidTestImplementation("com.google.dagger:hilt-android-testing:2.48")\n    kaptAndroidTest("com.google.dagger:hilt-android-compiler:2.48")\n}\n```\n\n## Core Testing Agent Components\n\n### 1. Test Generator Interface\n```kotlin\ninterface TestGenerator {\n    fun generateUnitTests(sourceFile: File): List<TestCase>\n    fun generateIntegrationTests(componentType: ComponentType): List<TestCase>\n    fun analyzeCodeCoverage(): CoverageReport\n}\n\ndata class TestCase(\n    val name: String,\n    val description: String,\n    val testCode: String,\n    val testType: TestType\n)\n\nenum class TestType { UNIT, INTEGRATION, E2E }\nenum class ComponentType { ACTIVITY, FRAGMENT, SERVICE, REPOSITORY, VIEWMODEL }\n```\n\n### 2. Unit Test Generator\n```kotlin\nclass UnitTestGenerator : TestGenerator {\n    \n    fun generateViewModelTests(viewModelClass: Class<*>): String {\n        return """\n        @RunWith(MockitoJUnitRunner::class)\n        class ${viewModelClass.simpleName}Test {\n            \n            @Mock\n            private lateinit var repository: Repository\n            \n            @Mock\n            private lateinit var scheduler: TestScheduler\n            \n            private lateinit var viewModel: ${viewModelClass.simpleName}\n            \n            @Before\n            fun setup() {\n                MockitoAnnotations.openMocks(this)\n                viewModel = ${viewModelClass.simpleName}(repository)\n            }\n            \n            @Test\n            fun `test initial state`() {\n                // Given - initial state\n                \n                // When - viewModel is created\n                \n                // Then - verify initial state\n                assertThat(viewModel.uiState.value).isEqualTo(expectedInitialState)\n            }\n            \n            @Test\n            fun `test loading data success`() {\n                // Given\n                val expectedData = createMockData()\n                whenever(repository.getData()).thenReturn(flowOf(expectedData))\n                \n                // When\n                viewModel.loadData()\n                \n                // Then\n                verify(repository).getData()\n                assertThat(viewModel.uiState.value.isLoading).isFalse()\n                assertThat(viewModel.uiState.value.data).isEqualTo(expectedData)\n            }\n            \n            @Test\n            fun `test loading data error`() {\n                // Given\n                val error = RuntimeException("Test error")\n                whenever(repository.getData()).thenReturn(flow { throw error })\n                \n                // When\n                viewModel.loadData()\n                \n                // Then\n                assertThat(viewModel.uiState.value.error).isEqualTo(error.message)\n            }\n        }\n        """.trimIndent()\n    }\n    \n    fun generateRepositoryTests(repositoryClass: Class<*>): String {\n        return """\n        @RunWith(MockitoJUnitRunner::class)\n        class ${repositoryClass.simpleName}Test {\n            \n            @Mock\n            private lateinit var apiService: ApiService\n            \n            @Mock\n            private lateinit var dao: Dao\n            \n            private lateinit var repository: ${repositoryClass.simpleName}\n            \n            @Before\n            fun setup() {\n                repository = ${repositoryClass.simpleName}(apiService, dao)\n            }\n            \n            @Test\n            fun `test fetch data from network success`() = runTest {\n                // Given\n                val networkData = createMockNetworkData()\n                whenever(apiService.fetchData()).thenReturn(networkData)\n                \n                // When\n                val result = repository.fetchData()\n                \n                // Then\n                verify(dao).insertAll(networkData)\n                assertThat(result.isSuccess).isTrue()\n            }\n            \n            @Test\n            fun `test fetch data from cache when network fails`() = runTest {\n                // Given\n                val cachedData = createMockCachedData()\n                whenever(apiService.fetchData()).thenThrow(IOException())\n                whenever(dao.getAllData()).thenReturn(flowOf(cachedData))\n                \n                // When\n                val result = repository.fetchData()\n                \n                // Then\n                verify(dao).getAllData()\n                assertThat(result.getOrNull()).isEqualTo(cachedData)\n            }\n        }\n        """.trimIndent()\n    }\n}\n```\n\n### 3. Integration Test Generator\n```kotlin\nclass IntegrationTestGenerator {\n    \n    fun generateActivityTests(activityClass: Class<*>): String {\n        return """\n        @RunWith(AndroidJUnit4::class)\n        @HiltAndroidTest\n        class ${activityClass.simpleName}IntegrationTest {\n            \n            @get:Rule\n            val hiltRule = HiltAndroidRule(this)\n            \n            @get:Rule\n            val activityRule = ActivityScenarioRule(${activityClass.simpleName}::class.java)\n            \n            @Before\n            fun setup() {\n                hiltRule.inject()\n            }\n            \n            @Test\n            fun testActivityLaunchesSuccessfully() {\n                // Verify activity launches and displays expected content\n                onView(withId(R.id.main_content))\n                    .check(matches(isDisplayed()))\n            }\n            \n            @Test\n            fun testUserInteractionFlow() {\n                // Test complete user flow\n                onView(withId(R.id.button_action))\n                    .perform(click())\n                \n                onView(withId(R.id.result_text))\n                    .check(matches(withText("Expected Result")))\n            }\n            \n            @Test\n            fun testNavigationBetweenScreens() {\n                // Test navigation flow\n                onView(withId(R.id.navigation_button))\n                    .perform(click())\n                \n                // Verify navigation to next screen\n                onView(withId(R.id.next_screen_indicator))\n                    .check(matches(isDisplayed()))\n            }\n        }\n        """.trimIndent()\n    }\n    \n    fun generateDatabaseTests(): String {\n        return """\n        @RunWith(AndroidJUnit4::class)\n        class DatabaseIntegrationTest {\n            \n            private lateinit var db: AppDatabase\n            private lateinit var dao: EntityDao\n            \n            @Before\n            fun createDb() {\n                val context = ApplicationProvider.getApplicationContext<Context>()\n                db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)\n                    .allowMainThreadQueries()\n                    .build()\n                dao = db.entityDao()\n            }\n            \n            @After\n            fun closeDb() {\n                db.close()\n            }\n            \n            @Test\n            fun testInsertAndRetrieve() = runTest {\n                // Given\n                val entity = createTestEntity()\n                \n                // When\n                dao.insert(entity)\n                val retrieved = dao.getById(entity.id)\n                \n                // Then\n                assertThat(retrieved).isEqualTo(entity)\n            }\n            \n            @Test\n            fun testComplexQuery() = runTest {\n                // Test complex database operations\n                val entities = createMultipleTestEntities()\n                dao.insertAll(entities)\n                \n                val result = dao.getEntitiesByCondition("test_condition")\n                \n                assertThat(result).hasSize(expectedCount)\n            }\n        }\n        """.trimIndent()\n    }\n}\n```\n\n## 4. Test Automation Agent\n\n### Main Agent Class\n```kotlin\nclass AndroidTestingAgent(\n    private val projectPath: String,\n    private val packageName: String\n) {\n    \n    private val unitTestGenerator = UnitTestGenerator()\n    private val integrationTestGenerator = IntegrationTestGenerator()\n    private val codeAnalyzer = CodeAnalyzer()\n    \n    fun generateAllTests() {\n        val sourceFiles = discoverSourceFiles()\n        \n        sourceFiles.forEach { file ->\n            when (determineFileType(file)) {\n                FileType.ACTIVITY -> generateActivityTests(file)\n                FileType.FRAGMENT -> generateFragmentTests(file)\n                FileType.VIEW_MODEL -> generateViewModelTests(file)\n                FileType.REPOSITORY -> generateRepositoryTests(file)\n                FileType.SERVICE -> generateServiceTests(file)\n            }\n        }\n    }\n    \n    private fun generateActivityTests(file: File) {\n        // Generate both unit and integration tests for activities\n        val unitTest = unitTestGenerator.generateActivityUnitTests(file)\n        val integrationTest = integrationTestGenerator.generateActivityTests(file.nameWithoutExtension::class.java)\n        \n        writeTestFile("${file.nameWithoutExtension}Test.kt", unitTest, TestDirectory.UNIT)\n        writeTestFile("${file.nameWithoutExtension}IntegrationTest.kt", integrationTest, TestDirectory.ANDROID_TEST)\n    }\n    \n    fun runTestSuite(): TestResults {\n        return TestResults(\n            unitTestResults = runUnitTests(),\n            integrationTestResults = runIntegrationTests(),\n            coverageReport = generateCoverageReport()\n        )\n    }\n    \n    private fun discoverSourceFiles(): List<File> {\n        return File("$projectPath/src/main/java")\n            .walkTopDown()\n            .filter { it.extension in listOf("kt", "java") }\n            .filter { !it.path.contains("/test/") }\n            .toList()\n    }\n}\n\nenum class FileType {\n    ACTIVITY, FRAGMENT, VIEW_MODEL, REPOSITORY, SERVICE, UTILITY\n}\n\nenum class TestDirectory {\n    UNIT, ANDROID_TEST\n}\n```\n\n## 5. Usage Examples\n\n### Creating and Running the Agent\n```kotlin\n// In your Claude Code script or build task\nfun main() {\n    val testingAgent = AndroidTestingAgent(\n        projectPath = System.getProperty("user.dir"),\n        packageName = "com.yourapp.package"\n    )\n    \n    // Generate all tests\n    testingAgent.generateAllTests()\n    \n    // Run tests and get results\n    val results = testingAgent.runTestSuite()\n    \n    println("Test Results:")\n    println("Unit Tests: ${results.unitTestResults.passed}/${results.unitTestResults.total}")\n    println("Integration Tests: ${results.integrationTestResults.passed}/${results.integrationTestResults.total}")\n    println("Coverage: ${results.coverageReport.percentage}%")\n}\n```\n\n## 6. Advanced Features\n\n### Mock Data Generator\n```kotlin\nclass MockDataGenerator {\n    fun generateMockUser() = User(\n        id = 1,\n        name = "Test User",\n        email = "test@example.com"\n    )\n    \n    fun generateMockApiResponse() = ApiResponse(\n        success = true,\n        data = listOf(generateMockUser()),\n        message = "Success"\n    )\n}\n```\n\n### Test Configuration\n```kotlin\n// Create testConfig.properties\nclass TestConfig {\n    companion object {\n        const val MOCK_SERVER_URL = "http://localhost:8080"\n        const val TEST_DATABASE_NAME = "test_database"\n        const val DEFAULT_TIMEOUT = 5000L\n    }\n}\n```\n\n## 7. Running Tests\n\n### Gradle Tasks\n```kotlin\n// In build.gradle.kts\ntasks.register("generateAndRunTests") {\n    dependsOn("generateTests", "testDebugUnitTest", "connectedDebugAndroidTest")\n    \n    doLast {\n        println("All tests generated and executed successfully!")\n    }\n}\n```\n\n## Best Practices\n\n1. **Test Naming**: Use descriptive names that explain what is being tested\n2. **Test Structure**: Follow Given-When-Then pattern\n3. **Mock Management**: Use dependency injection for easier mocking\n4. **Data Management**: Create test-specific data builders\n5. **Coverage**: Aim for 80%+ code coverage\n6. **Flaky Tests**: Implement retry mechanisms for integration tests\n7. **Performance**: Use test doubles to avoid network calls in unit tests\n\nThis agent will help you automatically generate comprehensive test suites for your Android application, covering both unit tests and integration tests with proper mocking and assertions.
model: sonnet
color: cyan
---

# Android Testing Agent for Java/Kotlin Projects

## Overview
This guide will help you create a comprehensive testing agent that can generate both unit tests and integration tests for your Android application.

## Project Structure Setup

### 1. Dependencies (build.gradle.kts - app module)
```kotlin
dependencies {
    // Testing frameworks
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.1.1")
    testImplementation("org.mockito:mockito-inline:5.1.1")
    testImplementation("org.mockito.kotlin:mockito-kotlin:4.1.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    
    // Android testing
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.test.espresso:espresso-contrib:3.5.1")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test:rules:1.5.0")
    androidTestImplementation("org.mockito:mockito-android:5.1.1")
    
    // Room testing
    testImplementation("androidx.room:room-testing:2.5.0")
    
    // Hilt testing
    androidTestImplementation("com.google.dagger:hilt-android-testing:2.48")
    kaptAndroidTest("com.google.dagger:hilt-android-compiler:2.48")
}
```

## Core Testing Agent Components

### 1. Test Generator Interface
```kotlin
interface TestGenerator {
    fun generateUnitTests(sourceFile: File): List<TestCase>
    fun generateIntegrationTests(componentType: ComponentType): List<TestCase>
    fun analyzeCodeCoverage(): CoverageReport
}

data class TestCase(
    val name: String,
    val description: String,
    val testCode: String,
    val testType: TestType
)

enum class TestType { UNIT, INTEGRATION, E2E }
enum class ComponentType { ACTIVITY, FRAGMENT, SERVICE, REPOSITORY, VIEWMODEL }
```

### 2. Unit Test Generator
```kotlin
class UnitTestGenerator : TestGenerator {
    
    fun generateViewModelTests(viewModelClass: Class<*>): String {
        return """
        @RunWith(MockitoJUnitRunner::class)
        class ${viewModelClass.simpleName}Test {
            
            @Mock
            private lateinit var repository: Repository
            
            @Mock
            private lateinit var scheduler: TestScheduler
            
            private lateinit var viewModel: ${viewModelClass.simpleName}
            
            @Before
            fun setup() {
                MockitoAnnotations.openMocks(this)
                viewModel = ${viewModelClass.simpleName}(repository)
            }
            
            @Test
            fun `test initial state`() {
                // Given - initial state
                
                // When - viewModel is created
                
                // Then - verify initial state
                assertThat(viewModel.uiState.value).isEqualTo(expectedInitialState)
            }
            
            @Test
            fun `test loading data success`() {
                // Given
                val expectedData = createMockData()
                whenever(repository.getData()).thenReturn(flowOf(expectedData))
                
                // When
                viewModel.loadData()
                
                // Then
                verify(repository).getData()
                assertThat(viewModel.uiState.value.isLoading).isFalse()
                assertThat(viewModel.uiState.value.data).isEqualTo(expectedData)
            }
            
            @Test
            fun `test loading data error`() {
                // Given
                val error = RuntimeException("Test error")
                whenever(repository.getData()).thenReturn(flow { throw error })
                
                // When
                viewModel.loadData()
                
                // Then
                assertThat(viewModel.uiState.value.error).isEqualTo(error.message)
            }
        }
        """.trimIndent()
    }
    
    fun generateRepositoryTests(repositoryClass: Class<*>): String {
        return """
        @RunWith(MockitoJUnitRunner::class)
        class ${repositoryClass.simpleName}Test {
            
            @Mock
            private lateinit var apiService: ApiService
            
            @Mock
            private lateinit var dao: Dao
            
            private lateinit var repository: ${repositoryClass.simpleName}
            
            @Before
            fun setup() {
                repository = ${repositoryClass.simpleName}(apiService, dao)
            }
            
            @Test
            fun `test fetch data from network success`() = runTest {
                // Given
                val networkData = createMockNetworkData()
                whenever(apiService.fetchData()).thenReturn(networkData)
                
                // When
                val result = repository.fetchData()
                
                // Then
                verify(dao).insertAll(networkData)
                assertThat(result.isSuccess).isTrue()
            }
            
            @Test
            fun `test fetch data from cache when network fails`() = runTest {
                // Given
                val cachedData = createMockCachedData()
                whenever(apiService.fetchData()).thenThrow(IOException())
                whenever(dao.getAllData()).thenReturn(flowOf(cachedData))
                
                // When
                val result = repository.fetchData()
                
                // Then
                verify(dao).getAllData()
                assertThat(result.getOrNull()).isEqualTo(cachedData)
            }
        }
        """.trimIndent()
    }
}
```

### 3. Integration Test Generator
```kotlin
class IntegrationTestGenerator {
    
    fun generateActivityTests(activityClass: Class<*>): String {
        return """
        @RunWith(AndroidJUnit4::class)
        @HiltAndroidTest
        class ${activityClass.simpleName}IntegrationTest {
            
            @get:Rule
            val hiltRule = HiltAndroidRule(this)
            
            @get:Rule
            val activityRule = ActivityScenarioRule(${activityClass.simpleName}::class.java)
            
            @Before
            fun setup() {
                hiltRule.inject()
            }
            
            @Test
            fun testActivityLaunchesSuccessfully() {
                // Verify activity launches and displays expected content
                onView(withId(R.id.main_content))
                    .check(matches(isDisplayed()))
            }
            
            @Test
            fun testUserInteractionFlow() {
                // Test complete user flow
                onView(withId(R.id.button_action))
                    .perform(click())
                
                onView(withId(R.id.result_text))
                    .check(matches(withText("Expected Result")))
            }
            
            @Test
            fun testNavigationBetweenScreens() {
                // Test navigation flow
                onView(withId(R.id.navigation_button))
                    .perform(click())
                
                // Verify navigation to next screen
                onView(withId(R.id.next_screen_indicator))
                    .check(matches(isDisplayed()))
            }
        }
        """.trimIndent()
    }
    
    fun generateDatabaseTests(): String {
        return """
        @RunWith(AndroidJUnit4::class)
        class DatabaseIntegrationTest {
            
            private lateinit var db: AppDatabase
            private lateinit var dao: EntityDao
            
            @Before
            fun createDb() {
                val context = ApplicationProvider.getApplicationContext<Context>()
                db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                    .allowMainThreadQueries()
                    .build()
                dao = db.entityDao()
            }
            
            @After
            fun closeDb() {
                db.close()
            }
            
            @Test
            fun testInsertAndRetrieve() = runTest {
                // Given
                val entity = createTestEntity()
                
                // When
                dao.insert(entity)
                val retrieved = dao.getById(entity.id)
                
                // Then
                assertThat(retrieved).isEqualTo(entity)
            }
            
            @Test
            fun testComplexQuery() = runTest {
                // Test complex database operations
                val entities = createMultipleTestEntities()
                dao.insertAll(entities)
                
                val result = dao.getEntitiesByCondition("test_condition")
                
                assertThat(result).hasSize(expectedCount)
            }
        }
        """.trimIndent()
    }
}
```

## 4. Test Automation Agent

### Main Agent Class
```kotlin
class AndroidTestingAgent(
    private val projectPath: String,
    private val packageName: String
) {
    
    private val unitTestGenerator = UnitTestGenerator()
    private val integrationTestGenerator = IntegrationTestGenerator()
    private val codeAnalyzer = CodeAnalyzer()
    
    fun generateAllTests() {
        val sourceFiles = discoverSourceFiles()
        
        sourceFiles.forEach { file ->
            when (determineFileType(file)) {
                FileType.ACTIVITY -> generateActivityTests(file)
                FileType.FRAGMENT -> generateFragmentTests(file)
                FileType.VIEW_MODEL -> generateViewModelTests(file)
                FileType.REPOSITORY -> generateRepositoryTests(file)
                FileType.SERVICE -> generateServiceTests(file)
            }
        }
    }
    
    private fun generateActivityTests(file: File) {
        // Generate both unit and integration tests for activities
        val unitTest = unitTestGenerator.generateActivityUnitTests(file)
        val integrationTest = integrationTestGenerator.generateActivityTests(file.nameWithoutExtension::class.java)
        
        writeTestFile("${file.nameWithoutExtension}Test.kt", unitTest, TestDirectory.UNIT)
        writeTestFile("${file.nameWithoutExtension}IntegrationTest.kt", integrationTest, TestDirectory.ANDROID_TEST)
    }
    
    fun runTestSuite(): TestResults {
        return TestResults(
            unitTestResults = runUnitTests(),
            integrationTestResults = runIntegrationTests(),
            coverageReport = generateCoverageReport()
        )
    }
    
    private fun discoverSourceFiles(): List<File> {
        return File("$projectPath/src/main/java")
            .walkTopDown()
            .filter { it.extension in listOf("kt", "java") }
            .filter { !it.path.contains("/test/") }
            .toList()
    }
}

enum class FileType {
    ACTIVITY, FRAGMENT, VIEW_MODEL, REPOSITORY, SERVICE, UTILITY
}

enum class TestDirectory {
    UNIT, ANDROID_TEST
}
```

## 5. Usage Examples

### Creating and Running the Agent
```kotlin
// In your Claude Code script or build task
fun main() {
    val testingAgent = AndroidTestingAgent(
        projectPath = System.getProperty("user.dir"),
        packageName = "com.yourapp.package"
    )
    
    // Generate all tests
    testingAgent.generateAllTests()
    
    // Run tests and get results
    val results = testingAgent.runTestSuite()
    
    println("Test Results:")
    println("Unit Tests: ${results.unitTestResults.passed}/${results.unitTestResults.total}")
    println("Integration Tests: ${results.integrationTestResults.passed}/${results.integrationTestResults.total}")
    println("Coverage: ${results.coverageReport.percentage}%")
}
```

## 6. Advanced Features

### Mock Data Generator
```kotlin
class MockDataGenerator {
    fun generateMockUser() = User(
        id = 1,
        name = "Test User",
        email = "test@example.com"
    )
    
    fun generateMockApiResponse() = ApiResponse(
        success = true,
        data = listOf(generateMockUser()),
        message = "Success"
    )
}
```

### Test Configuration
```kotlin
// Create testConfig.properties
class TestConfig {
    companion object {
        const val MOCK_SERVER_URL = "http://localhost:8080"
        const val TEST_DATABASE_NAME = "test_database"
        const val DEFAULT_TIMEOUT = 5000L
    }
}
```

## 7. Running Tests

### Gradle Tasks
```kotlin
// In build.gradle.kts
tasks.register("generateAndRunTests") {
    dependsOn("generateTests", "testDebugUnitTest", "connectedDebugAndroidTest")
    
    doLast {
        println("All tests generated and executed successfully!")
    }
}
```

## Best Practices

1. **Test Naming**: Use descriptive names that explain what is being tested
2. **Test Structure**: Follow Given-When-Then pattern
3. **Mock Management**: Use dependency injection for easier mocking
4. **Data Management**: Create test-specific data builders
5. **Coverage**: Aim for 80%+ code coverage
6. **Flaky Tests**: Implement retry mechanisms for integration tests
7. **Performance**: Use test doubles to avoid network calls in unit tests

This agent will help you automatically generate comprehensive test suites for your Android application, covering both unit tests and integration tests with proper mocking and assertions.
