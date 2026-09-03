package io.github.yingqiu0871.evolune.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.yingqiu0871.evolune.onboarding.OnboardingState
import io.github.yingqiu0871.evolune.onboarding.OnboardingStateStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class OnboardingViewModel(
    private val store: OnboardingStateStore
) : ViewModel() {
    val state: StateFlow<OnboardingState?> = store.state
        .map { state -> state.takeIf { it.initialized } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null
        )

    init {
        viewModelScope.launch {
            store.initializeIfNeeded()
        }
    }

    fun acceptTerms() {
        viewModelScope.launch { store.acceptTerms() }
    }

    fun acknowledgeMedicalPkDisclosure() {
        viewModelScope.launch { store.acknowledgeMedicalPkDisclosure() }
    }

    fun completeOnboarding() {
        viewModelScope.launch { store.completeOnboarding() }
    }

    fun markFeatureTutorialHandled() {
        viewModelScope.launch { store.markFeatureTutorialHandled() }
    }
}

class OnboardingViewModelFactory(
    private val store: OnboardingStateStore
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(OnboardingViewModel::class.java)) {
            return OnboardingViewModel(store) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
