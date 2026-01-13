package com.example.kotlinfrontend.ui

import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

/**
 * Simple "sticky header" decoration that pins the latest section header at the top while scrolling.
 *
 * It relies on the adapter exposing which positions are headers and providing/binding a header view.
 */
class NotificationsSectionHeaderDecoration(
    private val isHeader: (position: Int) -> Boolean,
    private val createHeaderView: (parent: RecyclerView) -> View,
    private val bindHeaderView: (header: View, headerPosition: Int) -> Unit
) : RecyclerView.ItemDecoration() {

    override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val topChild = parent.getChildAt(0) ?: return
        val topPos = parent.getChildAdapterPosition(topChild)
        if (topPos == RecyclerView.NO_POSITION) return

        val headerPos = findHeaderPositionForItem(topPos)
        if (headerPos == RecyclerView.NO_POSITION) return

        val header = getHeaderView(parent, headerPos)

        // Determine if next header is pushing the current one up.
        val contactPoint = header.bottom
        val childInContact = getChildInContact(parent, contactPoint)
        val shouldTranslate = childInContact != null && isHeader(parent.getChildAdapterPosition(childInContact))

        c.save()
        if (shouldTranslate && childInContact != null) {
            c.translate(0f, (childInContact.top - header.height).toFloat())
        } else {
            c.translate(0f, 0f)
        }
        header.draw(c)
        c.restore()
    }

    private fun getHeaderView(parent: RecyclerView, headerPosition: Int): View {
        val header = createHeaderView(parent)
        bindHeaderView(header, headerPosition)

        // Ensure layout/measurement.
        val widthSpec = View.MeasureSpec.makeMeasureSpec(parent.width, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(parent.height, View.MeasureSpec.UNSPECIFIED)
        val childWidth = ViewGroupMeasure.getChildMeasureSpec(
            widthSpec,
            parent.paddingLeft + parent.paddingRight,
            header.layoutParams?.width ?: ViewGroup.LayoutParams.MATCH_PARENT
        )
        val childHeight = ViewGroupMeasure.getChildMeasureSpec(
            heightSpec,
            parent.paddingTop + parent.paddingBottom,
            header.layoutParams?.height ?: ViewGroup.LayoutParams.WRAP_CONTENT
        )
        header.measure(childWidth, childHeight)
        header.layout(0, 0, header.measuredWidth, header.measuredHeight)
        return header
    }

    private fun findHeaderPositionForItem(itemPosition: Int): Int {
        var pos = itemPosition
        while (pos >= 0) {
            if (isHeader(pos)) return pos
            pos--
        }
        return RecyclerView.NO_POSITION
    }

    private fun getChildInContact(parent: RecyclerView, contactPoint: Int): View? {
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (child.top <= contactPoint && child.bottom >= contactPoint) return child
        }
        return null
    }

    /**
     * Avoid importing internal ViewGroup helpers; implement minimal getChildMeasureSpec.
     */
    private object ViewGroupMeasure {
        fun getChildMeasureSpec(spec: Int, padding: Int, childDimension: Int): Int {
            val specMode = View.MeasureSpec.getMode(spec)
            val specSize = View.MeasureSpec.getSize(spec)

            val size = (specSize - padding).coerceAtLeast(0)

            val resultSize: Int
            val resultMode: Int

            when (specMode) {
                View.MeasureSpec.EXACTLY -> {
                    when (childDimension) {
                        ViewGroup.LayoutParams.MATCH_PARENT -> {
                            resultSize = size
                            resultMode = View.MeasureSpec.EXACTLY
                        }
                        ViewGroup.LayoutParams.WRAP_CONTENT -> {
                            resultSize = size
                            resultMode = View.MeasureSpec.AT_MOST
                        }
                        else -> {
                            resultSize = childDimension
                            resultMode = View.MeasureSpec.EXACTLY
                        }
                    }
                }
                View.MeasureSpec.AT_MOST -> {
                    when (childDimension) {
                        ViewGroup.LayoutParams.MATCH_PARENT -> {
                            resultSize = size
                            resultMode = View.MeasureSpec.AT_MOST
                        }
                        ViewGroup.LayoutParams.WRAP_CONTENT -> {
                            resultSize = size
                            resultMode = View.MeasureSpec.AT_MOST
                        }
                        else -> {
                            resultSize = childDimension
                            resultMode = View.MeasureSpec.EXACTLY
                        }
                    }
                }
                else -> { // UNSPECIFIED
                    when (childDimension) {
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT -> {
                            resultSize = 0
                            resultMode = View.MeasureSpec.UNSPECIFIED
                        }
                        else -> {
                            resultSize = childDimension
                            resultMode = View.MeasureSpec.EXACTLY
                        }
                    }
                }
            }
            return View.MeasureSpec.makeMeasureSpec(resultSize, resultMode)
        }
    }
}
