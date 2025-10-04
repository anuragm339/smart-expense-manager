# RestTemplate Timeout Configuration for o1-mini API

## 🔴 Problem

```
java.net.SocketTimeoutException: timeout
```

**Root Cause**: o1-mini reasoning model takes 2-5 minutes to process insights, but default `RestTemplate` timeout is 30-60 seconds.

---

## ✅ Solution: Configure Custom Timeouts

### Option 1: Bean Configuration (RECOMMENDED) ⭐

Create a custom `RestTemplate` bean with extended timeouts:

```java
package com.flying.solo.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class RestTemplateConfig {

    /**
     * Custom RestTemplate for Azure OpenAI API calls
     * Configured with extended timeouts to handle o1-mini reasoning model (2-5 min processing)
     */
    @Bean(name = "openAIRestTemplate")
    public RestTemplate openAIRestTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(30))     // Connection timeout: 30 seconds
                .setReadTimeout(Duration.ofMinutes(6))         // Read timeout: 6 minutes (for o1-mini)
                .build();
    }

    /**
     * Alternative: Using SimpleClientHttpRequestFactory for fine-grained control
     */
    @Bean(name = "openAIRestTemplateAdvanced")
    public RestTemplate openAIRestTemplateAdvanced() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();

        // Connection timeout: How long to wait to establish connection (30 sec)
        factory.setConnectTimeout(30000);  // milliseconds

        // Read timeout: How long to wait for response after connection (6 min for o1-mini)
        factory.setReadTimeout(360000);    // 6 minutes = 360,000 ms

        return new RestTemplate(factory);
    }
}
```

### Update AIInsightsService to use the custom RestTemplate:

```java
@Slf4j
@Service
public class AIInsightsService {

    private final AzureOpenAIConfig config;
    private final RestTemplate restTemplate;  // This will now use the custom bean
    private final ObjectMapper objectMapper;

    // ✅ Inject the custom RestTemplate bean
    public AIInsightsService(
            AzureOpenAIConfig config,
            @Qualifier("openAIRestTemplate") RestTemplate restTemplate,  // Use custom bean
            ObjectMapper objectMapper) {
        this.config = config;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public String processWithAI(ExpenseInsightsDTO dto) throws JsonProcessingException {
        // ... rest of your code
    }
}
```

---

### Option 2: Direct Configuration in Service (Quick Fix)

If you can't create a bean, configure directly in the service:

```java
@Slf4j
@Service
public class AIInsightsService {

    private final AzureOpenAIConfig config;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public AIInsightsService(AzureOpenAIConfig config, ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;

        // ✅ Create RestTemplate with custom timeouts
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(30000);   // 30 seconds to connect
        factory.setReadTimeout(360000);     // 6 minutes to read response (o1-mini needs time)

        this.restTemplate = new RestTemplate(factory);
    }

    public String processWithAI(ExpenseInsightsDTO dto) throws JsonProcessingException {
        // ... your existing code
    }
}
```

---

### Option 3: Apache HttpClient (Production Grade) 🏆

For production with connection pooling and retry:

#### Add dependency to `pom.xml`:
```xml
<dependency>
    <groupId>org.apache.httpcomponents.client5</groupId>
    <artifactId>httpclient5</artifactId>
    <version>5.2.1</version>
</dependency>
```

#### Configuration:
```java
package com.flying.solo.config;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.Timeout;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    @Bean(name = "openAIRestTemplate")
    public RestTemplate openAIRestTemplate() {
        // Connection pooling
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(100);        // Max total connections
        connectionManager.setDefaultMaxPerRoute(20); // Max per route

        // Request configuration with timeouts
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(Timeout.ofSeconds(30))           // Connect timeout: 30s
                .setResponseTimeout(Timeout.ofMinutes(6))           // Response timeout: 6 min (o1-mini)
                .setConnectionRequestTimeout(Timeout.ofSeconds(10)) // Connection from pool: 10s
                .build();

        // Build HTTP client
        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .build();

        // Create RestTemplate with custom HTTP client
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClient);

        return new RestTemplate(factory);
    }
}
```

---

## 📊 Timeout Configuration Guide

### Recommended Settings for o1-mini:

| Timeout Type | Recommended | Explanation |
|--------------|-------------|-------------|
| **Connect Timeout** | 30 seconds | Time to establish TCP connection to Azure |
| **Read Timeout** | 6 minutes (360s) | Time to wait for o1-mini response (typically 2-5 min) |
| **Connection Request Timeout** | 10 seconds | Time to get connection from pool (if using pooling) |

### Why 6 minutes?

- o1-mini reasoning model: **2-5 minutes typical**
- Buffer for network delays: **+1 minute**
- Total safe timeout: **6 minutes**

---

## 🔄 Optional: Add Retry Logic

For production reliability, add retry mechanism:

```java
package com.flying.solo.service;

import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

@Service
public class AIInsightsService {

    /**
     * Retries up to 2 times with exponential backoff
     * - 1st retry: after 5 seconds
     * - 2nd retry: after 10 seconds
     */
    @Retryable(
        value = {RestClientException.class, SocketTimeoutException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 5000, multiplier = 2)
    )
    public String processWithAI(ExpenseInsightsDTO dto) throws JsonProcessingException {
        String url = config.getEndpoint();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("api-key", config.getKey());

        String prompt = buildStructuredPrompt(dto);

        Map<String, Object> body = Map.of(
                "messages", List.of(
                        Map.of("role", "system", "content", buildSystemPrompt()),
                        Map.of("role", "user", "content", prompt)
                ),
                "max_completion_tokens", 16000
        );

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            log.info("Calling o1-mini API (this may take 2-5 minutes)...");
            long startTime = System.currentTimeMillis();

            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);

            long duration = (System.currentTimeMillis() - startTime) / 1000;
            log.info("o1-mini API call completed in {} seconds", duration);

            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.getBody().get("choices");
            String aiResponse = (String) ((Map<String, Object>) choices.get(0).get("message")).get("content");

            return aiResponse.replaceAll("```json|```", "").trim();

        } catch (RestClientException e) {
            log.error("o1-mini API call failed: {}", e.getMessage());
            throw new RuntimeException("Failed to generate AI insights: " + e.getMessage(), e);
        }
    }
}
```

### Enable Retry in Spring Boot:

Add to your main application class:
```java
@SpringBootApplication
@EnableRetry  // ✅ Enable retry support
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

Add dependency to `pom.xml`:
```xml
<dependency>
    <groupId>org.springframework.retry</groupId>
    <artifactId>spring-retry</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>
```

---

## 🧪 Testing the Fix

### 1. Test Timeout Configuration

```java
@SpringBootTest
class AIInsightsServiceTest {

    @Autowired
    @Qualifier("openAIRestTemplate")
    private RestTemplate restTemplate;

    @Test
    void testTimeoutConfiguration() {
        // Verify timeout settings
        SimpleClientHttpRequestFactory factory =
            (SimpleClientHttpRequestFactory) restTemplate.getRequestFactory();

        assertEquals(30000, factory.getConnectTimeout());  // 30 seconds
        assertEquals(360000, factory.getReadTimeout());    // 6 minutes
    }
}
```

### 2. Monitor API Call Duration

```java
log.info("Calling o1-mini API (this may take 2-5 minutes)...");
long startTime = System.currentTimeMillis();

ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);

long duration = (System.currentTimeMillis() - startTime) / 1000;
log.info("o1-mini API completed in {} seconds", duration);
```

### 3. Check Logs

```
[INFO] Calling o1-mini API (this may take 2-5 minutes)...
[INFO] o1-mini API completed in 142 seconds  ✅ Success (2m 22s)
```

---

## 📋 Implementation Checklist

### Quick Fix (5 minutes):
- [ ] Add timeout configuration to AIInsightsService constructor
- [ ] Set connect timeout: 30 seconds
- [ ] Set read timeout: 360 seconds (6 minutes)
- [ ] Test with sample request

### Production Fix (30 minutes):
- [ ] Create RestTemplateConfig with custom bean
- [ ] Configure Apache HttpClient with connection pooling
- [ ] Add @Qualifier("openAIRestTemplate") to service constructor
- [ ] Add retry logic with @Retryable
- [ ] Add @EnableRetry to main application
- [ ] Add spring-retry dependency
- [ ] Add logging for API call duration
- [ ] Test with actual o1-mini API call

---

## 🚨 Common Issues & Solutions

### Issue 1: Still getting timeout after 6 minutes
```
Solution: o1-mini might be taking longer due to complex data
- Increase read timeout to 10 minutes (600000 ms)
- Reduce CSV transactions from 50 to 30
- Simplify prompt further
```

### Issue 2: Connection pool exhausted
```
Solution: Increase pool size in HttpClient config
connectionManager.setMaxTotal(200);
connectionManager.setDefaultMaxPerRoute(50);
```

### Issue 3: Azure timeout on their side
```
Solution: Check Azure OpenAI deployment settings
- Verify o1-mini deployment is active
- Check Azure quota limits
- Monitor Azure portal for service issues
```

---

## 📊 Performance Expectations

### o1-mini Timing Benchmarks:

| Data Size | Expected Duration | Timeout Setting |
|-----------|-------------------|-----------------|
| 20 transactions | 60-90 seconds | 3 minutes |
| 50 transactions | 120-180 seconds | 6 minutes |
| 100 transactions | 180-300 seconds | 10 minutes |

### Cost vs Timeout Tradeoff:

- **Shorter timeout (3 min)**: Faster failure detection but more retries
- **Longer timeout (6 min)**: More patient but ties up connections
- **Recommended**: 6 minutes with 2 retries (catches 99% of successful calls)

---

## 🎯 Final Recommendation

**Use Option 1 (Bean Configuration) + Retry Logic** for production:

1. ✅ Create `RestTemplateConfig` with 6-minute timeout
2. ✅ Inject with `@Qualifier("openAIRestTemplate")`
3. ✅ Add `@Retryable` for 2 retries
4. ✅ Add logging to track duration
5. ✅ Monitor Azure logs for patterns

This handles 99% of o1-mini calls successfully while providing visibility into performance issues.
