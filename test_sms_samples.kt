// Test SMS samples based on user's screenshot showing problematic merchants
// These should be REJECTED as non-transaction SMS

fun testProblematicSMSSamples() {
    val tester = SMSParsingTester()
    
    // Problematic SMS from screenshot - these should be rejected
    val problematicSamples = listOf(
        "PROMO" to "DELIVERY confirmation for your order #123456. Track at www.example.com",
        "INFO" to "GET INFORMATION about our latest credit card offers. Call 1800-XXX-XXXX",
        "BANK" to "INR500000 & PAY IN EASY EMI FROM INDUSLND BANK. Apply now for instant approval!",
        "OFFERS" to "Congratulations! You are eligible for a loan of Rs.5,00,000. Apply now!",
        "VERIFY" to "Your OTP for transaction is 123456. Valid for 10 minutes.",
        "UPDATE" to "Update your KYC details to continue using our services",
        "ALERT" to "New features available in our mobile app. Download now!"
    )
    
    // Valid transaction samples - these should be accepted
    val validSamples = listOf(
        "HDFCBK" to "Alert: You have spent Rs.250.00 on your HDFC Bank Credit Card at SWIGGY BANGALORE on 25-Dec-24 at 14:30",
        "ICICI" to "Rs.150 debited from A/c XX1234 for UPI txn at AMAZON on 26-Dec-24. Balance: Rs.5000",
        "SBI" to "Dear Customer, Rs.500 has been debited from your account XX1234 on 25-Dec-24 for purchase at ZOMATO"
    )
    
    println("=== TESTING PROBLEMATIC SMS (Should be REJECTED) ===")
    problematicSamples.forEach { (sender, sms) ->
        val result = tester.testSMSParsing(sms, sender)
        val status = if (result.isValidTransaction) "❌ INCORRECTLY ACCEPTED" else "✅ CORRECTLY REJECTED"
        println("$status: $sender - ${sms.take(40)}...")
        if (result.isValidTransaction) {
            println("  Issues: ${result.failureReasons.joinToString(", ")}")
        }
        println()
    }
    
    println("=== TESTING VALID SMS (Should be ACCEPTED) ===")
    validSamples.forEach { (sender, sms) ->
        val result = tester.testSMSParsing(sms, sender)
        val status = if (result.isValidTransaction) "✅ CORRECTLY ACCEPTED" else "❌ INCORRECTLY REJECTED"
        println("$status: $sender - ${sms.take(40)}...")
        if (!result.isValidTransaction) {
            println("  Issues: ${result.failureReasons.joinToString(", ")}")
        }
        println()
    }
}