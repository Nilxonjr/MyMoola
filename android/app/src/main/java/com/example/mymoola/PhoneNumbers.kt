package com.example.mymoola

private const val KenyaPrefix = "+254"

fun normalizeKenyanPhone(raw: String): String {
    val digits = raw.filter(Char::isDigit)
    if (digits.isEmpty()) return ""
    val local = when {
        digits.startsWith("254") -> digits.drop(3)
        digits.startsWith("0") -> digits.drop(1)
        else -> digits
    }.take(9)
    return if (local.length == 9) "$KenyaPrefix$local" else ""
}
