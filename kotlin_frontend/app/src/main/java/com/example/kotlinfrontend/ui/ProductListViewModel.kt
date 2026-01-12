package com.example.kotlinfrontend.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.kotlinfrontend.data.ProductPagingSource
import com.example.kotlinfrontend.data.ProductRepository
import com.example.kotlinfrontend.model.Product
import kotlinx.coroutines.flow.Flow

class ProductListViewModel : ViewModel() {

    private val repository = ProductRepository()

    private val pageSize = 20

    val products: Flow<PagingData<Product>> =
        Pager(
            config = PagingConfig(
                pageSize = pageSize,
                initialLoadSize = pageSize * 2, // efficient initial fill; still bounded
                prefetchDistance = 6,           // loads slightly ahead; avoids over-fetching
                enablePlaceholders = false
            ),
            pagingSourceFactory = { ProductPagingSource(repository, pageSize) }
        ).flow.cachedIn(viewModelScope)
}
