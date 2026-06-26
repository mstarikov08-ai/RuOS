package com.ruos.launcher

import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import kotlin.math.hypot

/**
 * Drag-to-reorder + folder-creation for a single [AppGridPage] in jiggle mode.
 *
 * Pickup: a press that moves past slop lifts the cell (spring to 1.12×, raised
 * elevation, haptic). While dragging, the cell tracks the finger 1:1 and the other
 * cells spring aside to open a gap (the page's moveCell reflow). Hovering the finger
 * over another cell's centre arms folder-creation; lifting there merges. Lifting
 * elsewhere drops into the open gap. All committed orders persist via the page.
 */
class DragController(private val page: AppGridPage) {

    private var dragged: View? = null
    private var pickedUp = false
    private var downX = 0f
    private var downY = 0f
    private var grabDX = 0f          // finger offset within the cell at pickup
    private var grabDY = 0f
    private var folderTarget = -1

    private val slop = 14f * page.resources.displayMetrics.density
    private val folderZone = 0.32f    // fraction of a cell around its centre

    fun isDragging(cell: View): Boolean = cell === dragged && pickedUp

    fun onInterceptTouch(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = ev.x; downY = ev.y }
            MotionEvent.ACTION_MOVE -> {
                if (!pickedUp && hypot(ev.x - downX, ev.y - downY) > slop) return true
            }
        }
        return false
    }

    fun onTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = event.x; downY = event.y }

            MotionEvent.ACTION_MOVE -> {
                if (!pickedUp) {
                    if (hypot(event.x - downX, event.y - downY) > slop) pickup(downX, downY)
                }
                if (pickedUp) follow(event.x, event.y)
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (pickedUp) drop()
        }
        return true
    }

    private fun pickup(x: Float, y: Float) {
        if (page.cellCount() == 0) return
        val index = page.indexAtPoint(x, y)
        val cell = page.cellAt(index)
        dragged = cell
        pickedUp = true
        grabDX = x - cell.translationX
        grabDY = y - cell.translationY
        folderTarget = -1

        cell.elevation = 24f
        cell.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        spring(cell, SpringAnimation.SCALE_X, 1.12f)
        spring(cell, SpringAnimation.SCALE_Y, 1.12f)
        spring(cell, SpringAnimation.ALPHA, 0.9f)
    }

    private fun follow(x: Float, y: Float) {
        val cell = dragged ?: return
        cell.translationX = x - grabDX
        cell.translationY = y - grabDY

        val hoverIndex = page.indexAtPoint(x, y)
        val currentIndex = page.indexOfCell(cell)
        if (hoverIndex == currentIndex) { clearFolderTarget(); return }

        // Folder arm: finger near the hovered slot's centre, both involve an app.
        val center = page.slotCenter(hoverIndex)
        val dist = hypot(x - center.x, y - center.y)
        val cellWidth = (page.slotCenter(1).x - page.slotCenter(0).x).coerceAtLeast(1f)
        val nearCenter = dist < folderZone * cellWidth
        val draggedIsApp = page.itemAt(currentIndex) is HomeItem.App
        if (nearCenter && draggedIsApp) {
            if (folderTarget != hoverIndex) {
                clearFolderTarget()
                folderTarget = hoverIndex
                spring(page.cellAt(hoverIndex), SpringAnimation.SCALE_X, 1.15f)
                spring(page.cellAt(hoverIndex), SpringAnimation.SCALE_Y, 1.15f)
            }
        } else {
            clearFolderTarget()
            page.moveCell(currentIndex, hoverIndex)
        }
    }

    private fun drop() {
        val cell = dragged ?: return
        val currentIndex = page.indexOfCell(cell)

        if (folderTarget >= 0 && folderTarget != currentIndex) {
            // Reset the target's pop before the merge rebuilds views.
            page.cellAt(folderTarget).apply { scaleX = 1f; scaleY = 1f }
            page.mergeIntoFolder(currentIndex, folderTarget)
        } else {
            // Settle into the open gap.
            spring(cell, SpringAnimation.TRANSLATION_X, page.slotX(currentIndex))
            spring(cell, SpringAnimation.TRANSLATION_Y, page.slotY(currentIndex))
            spring(cell, SpringAnimation.SCALE_X, 1f)
            spring(cell, SpringAnimation.SCALE_Y, 1f)
            spring(cell, SpringAnimation.ALPHA, 1f)
            cell.elevation = 0f
            page.commit()
        }
        Haptics.confirm(page)
        dragged = null
        pickedUp = false
        folderTarget = -1
    }

    private fun clearFolderTarget() {
        if (folderTarget >= 0 && folderTarget < page.cellCount()) {
            spring(page.cellAt(folderTarget), SpringAnimation.SCALE_X, 1f)
            spring(page.cellAt(folderTarget), SpringAnimation.SCALE_Y, 1f)
        }
        folderTarget = -1
    }

    private fun spring(view: View, prop: androidx.dynamicanimation.animation.DynamicAnimation.ViewProperty, target: Float) {
        SpringAnimation(view, prop).apply {
            spring = SpringForce(target).apply {
                stiffness = SpringForce.STIFFNESS_MEDIUM
                dampingRatio = SpringForce.DAMPING_RATIO_LOW_BOUNCY
            }
            start()
        }
    }
}
