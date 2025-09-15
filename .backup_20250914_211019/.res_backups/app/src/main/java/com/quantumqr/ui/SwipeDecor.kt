package com.quantumqr.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

class SwipeDecor(
    private val onSwiped: (position: Int, direction: Int) -> Unit = { _, _ -> }
) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {

    private val bg = Paint().apply { color = 0xFFE57373.toInt() } // soft red

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ) = false

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        onSwiped(viewHolder.bindingAdapterPosition, direction)
    }

    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        val item = viewHolder.itemView
        val rect = RectF(
            item.left.toFloat(),
            item.top.toFloat(),
            item.right.toFloat(),
            item.bottom.toFloat()
        )
        if (dX != 0f) c.drawRect(rect, bg)
        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
    }
}
