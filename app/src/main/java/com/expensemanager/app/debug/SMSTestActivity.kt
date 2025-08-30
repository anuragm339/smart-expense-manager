package com.expensemanager.app.debug

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.expensemanager.app.R

/**
 * Debug Activity for testing SMS parsing
 * Add this to your AndroidManifest.xml for testing:
 * 
 * <activity
 *     android:name=".debug.SMSTestActivity"
 *     android:label="SMS Parser Test"
 *     android:exported="false" />
 */
class SMSTestActivity : AppCompatActivity() {
    
    private lateinit var etSmsText: EditText
    private lateinit var etSenderAddress: EditText
    private lateinit var btnTest: Button
    private lateinit var btnTestSamples: Button
    private lateinit var tvResults: TextView
    private lateinit var scrollView: ScrollView
    
    private val tester = SMSParsingTester()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Create layout programmatically since we don't want to modify existing layouts
        createLayout()
        setupClickListeners()
    }
    
    private fun createLayout() {
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        
        // SMS Text input
        val tvSmsLabel = TextView(this).apply {
            text = "SMS Text:"
            textSize = 16f
            setPadding(0, 0, 0, 8)
        }
        
        etSmsText = EditText(this).apply {
            hint = "Paste SMS text here..."
            minLines = 3
            maxLines = 5
        }
        
        // Sender address input
        val tvSenderLabel = TextView(this).apply {
            text = "Sender Address:"
            textSize = 16f
            setPadding(0, 16, 0, 8)
        }
        
        etSenderAddress = EditText(this).apply {
            hint = "e.g., HDFCBK, ICICI, etc."
            maxLines = 1
        }
        
        // Test button
        btnTest = Button(this).apply {
            text = "Test SMS Parsing"
            setPadding(0, 16, 0, 16)
        }
        
        // Test samples button
        btnTestSamples = Button(this).apply {
            text = "Test Sample SMS"
            setPadding(0, 8, 0, 16)
        }
        
        // Results
        val tvResultsLabel = TextView(this).apply {
            text = "Results:"
            textSize = 16f
            setPadding(0, 16, 0, 8)
        }
        
        tvResults = TextView(this).apply {
            textSize = 12f
            typeface = android.graphics.Typeface.MONOSPACE
            setBackgroundColor(0xFFF0F0F0.toInt())
            setPadding(16, 16, 16, 16)
        }
        
        scrollView = ScrollView(this).apply {
            addView(tvResults)
        }
        
        // Add all views to layout
        layout.addView(tvSmsLabel)
        layout.addView(etSmsText)
        layout.addView(tvSenderLabel)
        layout.addView(etSenderAddress)
        layout.addView(btnTest)
        layout.addView(btnTestSamples)
        layout.addView(tvResultsLabel)
        layout.addView(scrollView)
        
        setContentView(layout)
    }
    
    private fun setupClickListeners() {
        btnTest.setOnClickListener {
            val smsText = etSmsText.text.toString().trim()
            val senderAddress = etSenderAddress.text.toString().trim()
            
            if (smsText.isEmpty()) {
                tvResults.text = "Please enter SMS text"
                return@setOnClickListener
            }
            
            testSingleSMS(smsText, senderAddress)
        }
        
        btnTestSamples.setOnClickListener {
            testSampleSMS()
        }
    }
    
    private fun testSingleSMS(smsText: String, senderAddress: String) {
        val result = tester.testSMSParsing(smsText, senderAddress)
        
        val output = buildString {
            appendLine("=== SMS PARSING TEST RESULT ===")
            appendLine()
            appendLine("SMS Text: ${smsText.take(100)}${if (smsText.length > 100) "..." else ""}")
            appendLine("Sender: $senderAddress")
            appendLine()
            appendLine("RESULT: ${if (result.isValidTransaction) "✅ VALID TRANSACTION" else "❌ NOT A TRANSACTION"}")
            appendLine("Confidence: ${(result.confidence * 100).toInt()}%")
            appendLine()
            appendLine("EXTRACTED DATA:")
            appendLine("• Amount: ₹${result.extractedAmount ?: "Not found"}")
            appendLine("• Merchant: ${result.extractedMerchant ?: "Not found"}")
            appendLine("• Bank: ${result.extractedBank}")
            appendLine()
            appendLine("DETECTION FLAGS:")
            result.detectionFlags.forEach { (flag, value) ->
                appendLine("• $flag: ${if (value) "✅" else "❌"}")
            }
            appendLine()
            if (result.failureReasons.isNotEmpty()) {
                appendLine("ISSUES FOUND:")
                result.failureReasons.forEach { reason ->
                    appendLine("• $reason")
                }
            }
            appendLine()
            appendLine("===========================")
        }
        
        tvResults.text = output
        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }
    
    private fun testSampleSMS() {
        // Test the problematic SMS from your screenshot
        val testSamples = listOf(
            // Your problematic samples
            "PROMO" to "DELIVERY confirmation for your order #123456. Track at www.example.com",
            "INFO" to "GET INFORMATION about our latest credit card offers. Call 1800-XXX-XXXX",
            "BANK" to "INR500000 & PAY IN EASY EMI FROM INDUSLND BANK. Apply now for instant approval!",
            
            // Valid transaction samples
            "HDFCBK" to "Alert: You have spent Rs.250.00 on your HDFC Bank Credit Card at SWIGGY BANGALORE on 25-Dec-24 at 14:30. Available limit: Rs.45,000.",
            "ICICI" to "Rs.150 debited from A/c XX1234 for UPI txn at AMAZON on 26-Dec-24. Balance: Rs.5000. UPI Ref: 123456789",
            "SBI" to "Dear Customer, Rs.500 has been debited from your account XX1234 on 25-Dec-24 for purchase at ZOMATO. Available balance Rs.10000",
            
            // Edge cases
            "PROMO" to "Congratulations! You are eligible for Rs.5,00,000 loan. Apply now!",
            "OTP" to "Your OTP for transaction is 123456. Valid for 10 minutes.",
            "NOTIF" to "Your card ending 1234 is due for renewal. Visit branch or call us."
        )
        
        val results = tester.testMultipleSMS(testSamples)
        
        val output = buildString {
            appendLine("=== BULK SMS PARSING TEST ===")
            appendLine()
            
            val validCount = results.count { it.isValidTransaction }
            val invalidCount = results.size - validCount
            
            appendLine("SUMMARY:")
            appendLine("• Total SMS tested: ${results.size}")
            appendLine("• Valid transactions: $validCount")
            appendLine("• Invalid/promotional: $invalidCount")
            appendLine("• Accuracy: ${(validCount.toFloat() / results.size * 100).toInt()}%")
            appendLine()
            appendLine("DETAILED RESULTS:")
            appendLine("=================")
            
            results.forEachIndexed { index, result ->
                appendLine()
                appendLine("${index + 1}. ${if (result.isValidTransaction) "✅ VALID" else "❌ INVALID"}")
                appendLine("SMS: ${result.smsText.take(60)}...")
                appendLine("Amount: ₹${result.extractedAmount ?: "None"}")
                appendLine("Merchant: ${result.extractedMerchant ?: "None"}")
                appendLine("Confidence: ${(result.confidence * 100).toInt()}%")
                if (result.failureReasons.isNotEmpty()) {
                    appendLine("Issues: ${result.failureReasons.joinToString(", ")}")
                }
                appendLine("---")
            }
            
            appendLine()
            appendLine("PROBLEMATIC SMS (Need fixing):")
            appendLine("==============================")
            results.filter { !it.isValidTransaction && it.extractedAmount != null }.forEach { result ->
                appendLine("• ${result.smsText.take(50)}...")
                appendLine("  Issues: ${result.failureReasons.joinToString(", ")}")
                appendLine()
            }
        }
        
        tvResults.text = output
        scrollView.post { scrollView.scrollTo(0, 0) }
    }
}