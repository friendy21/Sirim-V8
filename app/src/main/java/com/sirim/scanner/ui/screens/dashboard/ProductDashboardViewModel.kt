package com.sirim.scanner.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sirim.scanner.data.db.ProductScan
import com.sirim.scanner.data.repository.SirimRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProductDashboardViewModel private constructor(
    private val repository: SirimRepository
) : ViewModel() {

    val productScans: StateFlow<List<ProductScan>> = repository.productScans
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun deleteScan(scan: ProductScan) {
        viewModelScope.launch { repository.deleteProductScan(scan) }
    }

    fun updateScan(scan: ProductScan) {
        viewModelScope.launch { repository.updateProductScan(scan) }
    }

    fun updateSirimData(id: Long, data: String?) {
        viewModelScope.launch { repository.updateProductScanSirimData(id, data) }
    }

    companion object {
        fun Factory(repository: SirimRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ProductDashboardViewModel(repository) as T
                }
            }
    }
}
