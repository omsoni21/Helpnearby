package com.example.helpnearby.core.security

import com.example.helpnearby.domain.model.Conversation
import com.example.helpnearby.domain.model.User
import com.example.helpnearby.domain.model.UserRole
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecurityGuard @Inject constructor() {

    private val recentActionTimestamps = mutableMapOf<String, Long>()

    fun hashPasswordLocal(password: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(password.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun canModifyOwnedResource(actorId: String, ownerId: String): Boolean {
        return actorId.isNotBlank() && actorId == ownerId
    }

    fun canAccessConversation(userId: String, conversation: Conversation): Boolean {
        return userId.isNotBlank() &&
            (conversation.ownerId == userId || conversation.volunteerId == userId)
    }

    /**
     * Admin privileges must only be granted by a trusted server claim (Firebase custom claim / Supabase JWT role).
     * Never trust client-mutable fields in production.
     */
    fun canPerformAdminModeration(actor: User, isServerVerifiedAdmin: Boolean): Boolean {
        return actor.role == UserRole.ADMIN && isServerVerifiedAdmin
    }

    /**
     * Simple client-side throttle to prevent accidental rapid duplicate submissions.
     * Real rate-limiting must also be enforced at the backend/database rules layer.
     */
    @Synchronized
    fun allowActionWithRateLimit(actionKey: String, minIntervalMs: Long = 1_500L): Boolean {
        val now = System.currentTimeMillis()
        val previous = recentActionTimestamps[actionKey]
        if (previous != null && (now - previous) < minIntervalMs) {
            return false
        }
        recentActionTimestamps[actionKey] = now
        return true
    }
}
