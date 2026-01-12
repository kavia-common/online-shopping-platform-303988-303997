package com.example.kotlinfrontend.ui

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

/**
 * RecyclerView ItemDecoration that adds horizontal spacing between items.
 */
class HorizontalSpaceItemDecoration(
    private val startPaddingPx: Int,
    private val itemSpacingPx: Int,
    private val endPaddingPx: Int
) : RecyclerView.ItemDecoration() {

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        val position = parent.getChildAdapterPosition(view)
        val itemCount = state.itemCount

        if (position == RecyclerView.NO_POSITION) return

        outRect.top = 0
        outRect.bottom = 0

        // Add start padding for the first item, end padding for the last item,
        // and spacing between items.
        outRect.left = if (position == 0) startPaddingPx else itemSpacingPx
        outRect.right = if (position == itemCount - 1) endPaddingPx else 0
    }
}
