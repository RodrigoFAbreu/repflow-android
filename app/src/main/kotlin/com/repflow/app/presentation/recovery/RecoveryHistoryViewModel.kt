package com.repflow.app.presentation.recovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.repflow.app.application.recovery.FutsalRepository
import com.repflow.app.application.recovery.RecoveryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Owns the read-only recovery/futsal history list state (Milestone 8, CP4).
 * Both repositories already expose `findAll()` (used previously only by
 * backup export); this is the first UI consumer.
 */
@HiltViewModel
class RecoveryHistoryViewModel
    @Inject
    constructor(
        private val recoveryRepository: RecoveryRepository,
        private val futsalRepository: FutsalRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(RecoveryHistoryUiState())
        val uiState = _uiState.asStateFlow()

        init {
            viewModelScope.launch {
                val recoveryEntries = recoveryRepository.findAll()
                val futsalSessions = futsalRepository.findAll()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        recoveryEntries = recoveryEntries,
                        futsalSessions = futsalSessions,
                    )
                }
            }
        }
    }
