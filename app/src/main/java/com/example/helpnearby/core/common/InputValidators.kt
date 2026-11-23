package com.example.helpnearby.core.common

object InputValidators {
    private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    private val INDIAN_PHONE_OR_LONG_DIGITS = Regex("(?:\\+91[-\\s]?)?[6-9]\\d{9}|\\d{10,}")

    fun validateEmail(email: String): String? {
        val trimmed = email.trim()
        if (trimmed.isBlank()) return "Email is required."
        if (!EMAIL_REGEX.matches(trimmed)) return "Please enter a valid email address."
        return null
    }

    fun validatePassword(password: String): String? {
        if (password.length < 8) return "Password must be at least 8 characters."
        if (!password.any { it.isDigit() }) return "Password must include at least one number."
        if (!password.any { it.isLetter() }) return "Password must include at least one letter."
        return null
    }

    fun validateFullName(name: String): String? {
        val trimmed = name.trim()
        if (trimmed.length < 2) return "Please enter your full name."
        if (trimmed.length > 80) return "Name must be 80 characters or fewer."
        return null
    }

    fun validateHelpRequest(
        title: String,
        description: String,
        city: String,
        state: String,
        approximateArea: String
    ): String? {
        if (title.trim().length < 5) return "Request title must be at least 5 characters."
        if (title.trim().length > 100) return "Request title must be 100 characters or fewer."
        if (description.trim().length < 15) return "Please describe the help needed (at least 15 characters)."
        if (city.trim().isBlank()) return "City is required."
        if (state.trim().isBlank()) return "State is required."
        if (approximateArea.trim().isBlank()) return "Approximate area or neighborhood is required."
        if (containsPrivatePhoneNumber(description) || containsPrivatePhoneNumber(title)) {
            return "For your privacy, do not include phone numbers in public listings. Use in-app private messaging after accepting help."
        }
        return null
    }

    fun containsPrivatePhoneNumber(text: String): Boolean {
        return INDIAN_PHONE_OR_LONG_DIGITS.containsMatchIn(text.replace(" ", ""))
    }

    fun validateCityAndArea(city: String, state: String): String? {
        if (city.trim().isBlank()) return "City cannot be empty."
        if (state.trim().isBlank()) return "State cannot be empty."
        return null
    }

    fun validateImageSizeBytes(sizeBytes: Long, maxBytes: Long = 5L * 1024L * 1024L): String? {
        if (sizeBytes <= 0L) return null
        if (sizeBytes > maxBytes) return "Image must be smaller than 5 MB."
        return null
    }

    fun sanitizeErrorMessage(raw: String?): String {
        if (raw.isNullOrBlank()) return "Something went wrong. Please try again."
        return raw.take(180)
    }
}
