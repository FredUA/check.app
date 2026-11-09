package com.example.ui.viewmodel

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.TaskFlowRepository
import com.example.data.model.UserProfile
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private const val TAG = "AuthViewModel"

sealed interface AuthUiState {
    data object Loading : AuthUiState
    data object Unauthenticated : AuthUiState
    data class Authenticated(val user: FirebaseUser, val profile: UserProfile?) : AuthUiState
    data class Error(val message: String) : AuthUiState
}

class AuthViewModel(
    private val repository: TaskFlowRepository
) : ViewModel() {

    private val auth = Firebase.auth
    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Loading)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user == null) {
                _uiState.value = AuthUiState.Unauthenticated
            } else {
                viewModelScope.launch {
                    repository.observeUserProfile(user.uid).collect { profile ->
                        _uiState.value = AuthUiState.Authenticated(user, profile)
                    }
                }
            }
        }
    }

    fun attemptAutoSignIn(context: Context) {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            viewModelScope.launch {
                repository.observeUserProfile(currentUser.uid).collect { profile ->
                    _uiState.value = AuthUiState.Authenticated(currentUser, profile)
                }
            }
            return
        }

        val clientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (e: Exception) {
            Log.w(TAG, "default_web_client_id not found: ${e.message}")
            _uiState.value = AuthUiState.Unauthenticated
            return
        }

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(true)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val credentialManager = CredentialManager.create(context)
        viewModelScope.launch {
            try {
                val result = credentialManager.getCredential(context, request)
                val credential = result.credential
                if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                    val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                    val authResult = auth.signInWithCredential(authCredential).await()
                    val user = authResult.user
                    if (user != null) {
                        saveDefaultProfile(user)
                    }
                } else {
                    _uiState.value = AuthUiState.Unauthenticated
                }
            } catch (e: Exception) {
                Log.d(TAG, "Silent auto sign-in skipped: ${e.message}")
                _uiState.value = AuthUiState.Unauthenticated
            }
        }
    }

    fun signInWithGoogle(activity: Activity) {
        _uiState.value = AuthUiState.Loading
        val clientId = try {
            activity.getString(R.string.default_web_client_id)
        } catch (e: Exception) {
            _uiState.value = AuthUiState.Error("Google Sign-In configuration missing")
            return
        }

        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(signInOption).build()
        val credentialManager = CredentialManager.create(activity)

        viewModelScope.launch {
            try {
                val result = credentialManager.getCredential(activity, request)
                val credential = result.credential
                if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                    val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                    val authResult = auth.signInWithCredential(authCredential).await()
                    val user = authResult.user
                    if (user != null) {
                        saveDefaultProfile(user)
                    }
                } else {
                    _uiState.value = AuthUiState.Error("Unexpected credential type")
                }
            } catch (e: GetCredentialCancellationException) {
                Log.w(TAG, "Google Sign-In cancelled: ${e.message}", e)
                _uiState.value = AuthUiState.Unauthenticated
            } catch (e: Exception) {
                Log.e(TAG, "Google Sign-In failed", e)
                _uiState.value = AuthUiState.Error(e.localizedMessage ?: "Sign-in failed")
            }
        }
    }

    private suspend fun saveDefaultProfile(user: FirebaseUser) {
        val names = user.displayName.orEmpty().split(" ")
        val firstName = names.firstOrNull().orEmpty()
        val lastName = if (names.size > 1) names.drop(1).joinToString(" ") else ""
        val profile = UserProfile(
            id = user.uid,
            email = user.email.orEmpty(),
            displayName = user.displayName.orEmpty(),
            firstName = firstName,
            lastName = lastName,
            avatarUrl = user.photoUrl?.toString().orEmpty()
        )
        // Only the first sign-in takes the data from Google; later ones keep what the user saved.
        repository.createUserProfileIfMissing(profile)
    }

    fun updateProfile(firstName: String, lastName: String, avatarUrl: String) {
        val user = auth.currentUser ?: return
        val displayName = listOfNotNull(firstName.trim(), lastName.trim()).filter { it.isNotBlank() }.joinToString(" ")
        viewModelScope.launch {
            try {
                val profile = UserProfile(
                    id = user.uid,
                    email = user.email.orEmpty(),
                    displayName = displayName.ifBlank { user.email.orEmpty() },
                    firstName = firstName.trim(),
                    lastName = lastName.trim(),
                    avatarUrl = avatarUrl.trim()
                )
                repository.saveUserProfile(profile)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update profile", e)
            }
        }
    }

    fun signOut(context: Context) {
        val credentialManager = CredentialManager.create(context)
        viewModelScope.launch {
            // Stop pushes for this account on this device. Must happen while still signed in (security rules).
            try {
                kotlinx.coroutines.withTimeoutOrNull(3_000) { repository.unregisterDevice() }
            } catch (e: Exception) {
                Log.w(TAG, "Could not unregister device for push", e)
            }
            auth.signOut()
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear credential state", e)
            } finally {
                _uiState.value = AuthUiState.Unauthenticated
            }
        }
    }
}
