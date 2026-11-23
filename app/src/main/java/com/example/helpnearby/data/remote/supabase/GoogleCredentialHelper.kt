package com.example.helpnearby.data.remote.supabase

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.helpnearby.core.network.BackendConfig
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

sealed interface GoogleCredentialResult {
    data class IdToken(val idToken: String, val rawNonce: String) : GoogleCredentialResult
    data object Cancelled : GoogleCredentialResult
    data class Unavailable(val reason: String) : GoogleCredentialResult
}

/**
 * Launches Jetpack Credential Manager with [GetSignInWithGoogleOption] when `GOOGLE_WEB_CLIENT_ID`
 * is configured via AI Studio Secrets / BuildConfig, returning a verified Google ID Token + nonce.
 */
@Singleton
class GoogleCredentialHelper @Inject constructor(
    private val backendConfig: BackendConfig
) {
    suspend fun requestGoogleIdToken(activityContext: Context): GoogleCredentialResult {
        val webClientId = backendConfig.googleWebClientId
        if (!backendConfig.isGoogleCredentialManagerConfigured) {
            return GoogleCredentialResult.Unavailable("GOOGLE_WEB_CLIENT_ID is not configured.")
        }

        val rawNonce = UUID.randomUUID().toString()
        val hashedNonce = sha256Hex(rawNonce)

        val option = GetSignInWithGoogleOption.Builder(serverClientId = webClientId)
            .setNonce(hashedNonce)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()

        val credentialManager = CredentialManager.create(activityContext)
        return try {
            val response = credentialManager.getCredential(
                context = activityContext,
                request = request
            )
            val cred = response.credential
            if (cred is CustomCredential &&
                cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleCred = GoogleIdTokenCredential.createFrom(cred.data)
                GoogleCredentialResult.IdToken(
                    idToken = googleCred.idToken,
                    rawNonce = rawNonce
                )
            } else {
                GoogleCredentialResult.Unavailable("Unsupported Google credential returned.")
            }
        } catch (_: GetCredentialCancellationException) {
            GoogleCredentialResult.Cancelled
        } catch (e: NoCredentialException) {
            GoogleCredentialResult.Unavailable(
                e.errorMessage?.toString()
                    ?: "No Google account available on this device."
            )
        } catch (e: GoogleIdTokenParsingException) {
            GoogleCredentialResult.Unavailable(
                e.message ?: "Unable to parse Google ID token."
            )
        } catch (e: GetCredentialException) {
            GoogleCredentialResult.Unavailable(
                e.errorMessage?.toString() ?: e.message ?: "Google Sign-In failed."
            )
        }
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
