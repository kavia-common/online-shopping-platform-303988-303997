package com.example.kotlinfrontend.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.paging.LoadState
import androidx.paging.LoadStateAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.kotlinfrontend.databinding.ItemLoadStateFooterBinding

class ProductLoadStateAdapter(
    private val onRetry: () -> Unit
) : LoadStateAdapter<ProductLoadStateAdapter.LoadStateViewHolder>() {

    class LoadStateViewHolder(
        private val binding: ItemLoadStateFooterBinding,
        private val onRetry: () -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(loadState: LoadState) {
            val isLoading = loadState is LoadState.Loading
            val isError = loadState is LoadState.Error

            binding.progress.isVisible = isLoading
            binding.retry.isVisible = isError
            binding.message.isVisible = isError

            if (isError) {
                binding.message.text = (loadState as LoadState.Error).error.message ?: "Load failed"
            }

            binding.retry.setOnClickListener { onRetry() }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, loadState: LoadState): LoadStateViewHolder {
        val binding = ItemLoadStateFooterBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return LoadStateViewHolder(binding, onRetry)
    }

    override fun onBindViewHolder(holder: LoadStateViewHolder, loadState: LoadState) {
        holder.bind(loadState)
    }
}
