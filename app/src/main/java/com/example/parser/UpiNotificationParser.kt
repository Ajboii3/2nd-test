package com.example.parser

import android.util.Log

data class ParsedDonation(
    val senderName: String,
    val amount: String,
    val appName: String,
    val isPayment: Boolean
)

object UpiNotificationParser {
    private const val TAG = "UpiNotificationParser"

    // Patterns designed for UPI apps
    private val PATTERNS = listOf(
        // Pattern 1: "Received ₹100 from Ramesh Singh"
        Regex("""(?i)Received\s+(?:Rs\.?|₹)\s*([\d,.]+)\s+from\s+(.+)"""),

        // Pattern 2: "₹100.00 successfully received from Ramesh Kumar"
        Regex("""(?i)(?:Rs\.?|₹)\s*([\d,.]+)\s+(?:received|credited|successful|successfully\s+received)\s+from\s+(.+)"""),

        // Pattern 3: GPay/PhonePe "Anoop paid you ₹150.00"
        Regex("""(?i)(.+?)\s+(?:paid|Paid|sent|transferred|credited)\s+you\s+(?:Rs\.?|₹)\s*([\d,.]+)"""),

        // Pattern 4: "You have received ₹150 from Amit Kumar"
        Regex("""(?i)You\s+have\s+received\s+(?:Rs\.?|₹)\s*([\d,.]+)\s+from\s+(.+)"""),

        // Pattern 5: Paytm Business Merchant credits
        // "₹150 received in Paytm. Merchant Payment"
        Regex("""(?i)Received\s+(?:Rs\.?|₹)\s*([\d,.]+)\s+(?:in\s+Paytm|in\s+wallet)"""),

        // Pattern 6: SMS/Generic bank notification credits:
        Regex("""(?i)(?:Credit|Credited|Received)\s+(?:with|of)?\s*(?:INR|Rs\.?|₹)\s*([\d,.]+)\s+by\s+(.+)"""),
        Regex("""(?i)(?:Credit|Credited|Received)\s+(?:with|of)?\s*(?:INR|Rs\.?|₹)\s*([\d,.]+)\s+(?:from|by)\s+(.+)""")
    )

    fun parse(title: String?, text: String?, packageName: String?): ParsedDonation {
        val appName = getAppName(packageName, title, text)
        val combinedText = "${title ?: ""} ${text ?: ""}".trim()

        if (combinedText.isEmpty()) {
            return ParsedDonation("Anonymous", "0", appName, false)
        }

        Log.d(TAG, "Parsing text: [$combinedText] from package: $packageName")

        for (regex in PATTERNS) {
            val matchResult = regex.find(combinedText)
            if (matchResult != null) {
                try {
                    val groups = matchResult.groupValues
                    if (groups.size >= 3) {
                        val val1 = groups[1].trim()
                        val val2 = groups[2].trim()

                        val isVal1Numeric = isNumeric(val1)
                        val isVal2Numeric = isNumeric(val2)

                        val amount: String
                        val rawSender: String

                        if (isVal1Numeric && !isVal2Numeric) {
                            amount = val1
                            rawSender = val2
                        } else if (!isVal1Numeric && isVal2Numeric) {
                            amount = val2
                            rawSender = val1
                        } else {
                            amount = val1
                            rawSender = val2
                        }

                        val sender = sanitizeSenderName(rawSender)
                        return ParsedDonation(
                            senderName = sender,
                            amount = cleanAmount(amount),
                            appName = appName,
                            isPayment = true
                        )
                    } else if (groups.size == 2) {
                        val amount = groups[1].trim()
                        if (isNumeric(amount)) {
                            return ParsedDonation(
                                senderName = "Anonymous Merchant",
                                amount = cleanAmount(amount),
                                appName = appName,
                                isPayment = true
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Regex group matching error: ${e.message}", e)
                }
            }
        }

        // Fallback for simple fragments like "₹120 received"
        val genericMoneyRegex = Regex("""(?:Rs\.?|₹)\s*([\d,.]+)""", RegexOption.IGNORE_CASE)
        val fallbackMatch = genericMoneyRegex.find(combinedText)
        if (fallbackMatch != null && (combinedText.contains("receive", true) || combinedText.contains("paid", true) || combinedText.contains("sent", true) || combinedText.contains("credit", true))) {
            val amount = fallbackMatch.groupValues[1]
            return ParsedDonation(
                senderName = "Anonymous User",
                amount = cleanAmount(amount),
                appName = appName,
                isPayment = true
            )
        }

        return ParsedDonation("Anonymous", "0", appName, false)
    }

    private fun isNumeric(str: String): Boolean {
        val clean = str.replace(",", "").replace(".", "").trim()
        return clean.isNotEmpty() && clean.all { it.isDigit() }
    }

    private fun cleanAmount(amount: String): String {
        var cleaned = amount.replace(",", "").trim()
        if (cleaned.endsWith(".")) {
            cleaned = cleaned.substring(0, cleaned.length - 1)
        }
        return cleaned
    }

    private fun sanitizeSenderName(name: String): String {
        var s = name.trim()

        while (s.isNotEmpty() && (s.endsWith(".") || s.endsWith(",") || s.endsWith("!") || s.endsWith("?"))) {
            s = s.substring(0, s.length - 1).trim()
        }

        val phrasesToRemove = listOf(
            "successfully",
            "successful",
            "in paytm business",
            "in paytm",
            "merchant payment",
            "into wallet",
            "to you",
            "on phonepe"
        )

        for (phrase in phrasesToRemove) {
            s = s.replace(Regex("(?i)\\b$phrase\\b"), "").trim()
        }

        s = s.replace(Regex("""\s+"""), " ")

        if (s.isEmpty() || s.length < 2 || isNumeric(s)) {
            return "Anonymous User"
        }

        return s
    }

    private fun getAppName(packageName: String?, title: String?, text: String?): String {
        val comb = "${packageName ?: ""} ${title ?: ""} ${text ?: ""}".lowercase()
        return when {
            comb.contains("paytm.business") || comb.contains("paytmbusiness") -> "Paytm Business"
            comb.contains("paytm") -> "Paytm"
            comb.contains("phonepe.business") || comb.contains("phonepebusiness") -> "PhonePe Business"
            comb.contains("phonepe") -> "PhonePe"
            comb.contains("paisa") || comb.contains("gpay") || comb.contains("google.android.apps.nbu.paisa") -> "Google Pay"
            comb.contains("bhim") || comb.contains("npci.upiapp") -> "BHIM"
            comb.contains("amazon") -> "Amazon Pay"
            comb.contains("cred") -> "CRED"
            else -> "UPI"
        }
    }
}
