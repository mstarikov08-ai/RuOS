package com.ruos.launcher

import android.content.Context
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce

/**
 * One page of the home screen — a COLS×ROWS grid of app icons and folders.
 *
 * Rewritten from GridLayout to a custom spring-reflow ViewGroup so that, in jiggle
 * mode, icons can be picked up and dragged with neighbours springing aside to open
 * a gap (iOS-style), and dropping one icon on another creates a folder. Each cell is
 * laid out at (0,0) and positioned purely via translationX/Y, so reflow is a spring
 * on translation rather than a relayout.
 */
class AppGridPage @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ViewGroup(context, attrs) {

    var onAppLongPress: (() -> Unit)? = null          // enters jiggle mode
    var onOpenFolder: ((FolderIcon) -> Unit)? = null
    var onLayoutCommitted: (() -> Unit)? = null       // model changed → persist+rebuild

    private val cols = HomePagePager.COLS
    private val rows = HomePagePager.ROWS

    private val items = mutableListOf<HomeItem>()
    private val cells = mutableListOf<View>()
    private var jiggle = false

    private var cellW = 0
    private var cellH = 0

    private val drag = DragController(this)

    // ── population ──────────────────────────────────────────────────────────────

    fun setItems(list: List<HomeItem>) {
        items.clear(); items.addAll(list)
        removeAllViews(); cells.clear()
        for (item in items) cells.add(createCell(item))
        cells.forEach { addView(it) }
        applySlots(animate = false)
    }

    fun currentItems(): List<HomeItem> = items.toList()

    private fun createCell(item: HomeItem): View = when (item) {
        is HomeItem.App -> AppIconView(context).apply {
            bind(item.info)
            onLongPress = { enterJiggleAndPickup(this) }
        }
        is HomeItem.Folder -> FolderIcon(context).apply {
            bind(item)
            onLongPress = { enterJiggleAndPickup(this) }
            onOpen = { fi -> onOpenFolder?.invoke(fi) }
        }
    }

    private fun enterJiggleAndPickup(cell: View) {
        if (!jiggle) onAppLongPress?.invoke()   // HomeView flips everyone into jiggle
    }

    fun setJiggleMode(active: Boolean) {
        jiggle = active
        cells.forEach { (it as? AppIconView)?.setJiggleMode(active) }
    }

    /** Find the app icon view for a package on this page (not inside folders), or null. */
    fun findIcon(pkg: String): AppIconView? =
        cells.firstOrNull { it is AppIconView && it.packageName == pkg } as? AppIconView

    // ── geometry ──────────────────────────────────────────────────────────────

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        cellW = if (cols > 0) w / cols else w
        cellH = if (rows > 0) h / rows else h
        val cw = MeasureSpec.makeMeasureSpec(cellW, MeasureSpec.EXACTLY)
        val ch = MeasureSpec.makeMeasureSpec(cellH, MeasureSpec.EXACTLY)
        cells.forEach { it.measure(cw, ch) }
        setMeasuredDimension(w, h)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        cells.forEachIndexed { index, cell ->
            cell.layout(0, 0, cellW, cellH)
            if (!drag.isDragging(cell)) {
                cell.translationX = slotX(index)
                cell.translationY = slotY(index)
            }
        }
    }

    fun slotX(index: Int): Float = ((index % cols) * cellW).toFloat()
    fun slotY(index: Int): Float = ((index / cols) * cellH).toFloat()
    fun slotCenter(index: Int) = PointF(slotX(index) + cellW / 2f, slotY(index) + cellH / 2f)

    /** Index of the slot a local point falls in, clamped to the item count. */
    fun indexAtPoint(x: Float, y: Float): Int {
        val c = (x / cellW).toInt().coerceIn(0, cols - 1)
        val rr = (y / cellH).toInt().coerceIn(0, rows - 1)
        return (rr * cols + c).coerceIn(0, (cells.size - 1).coerceAtLeast(0))
    }

    fun cellCount() = cells.size
    fun cellAt(index: Int): View = cells[index]
    fun itemAt(index: Int): HomeItem = items[index]
    fun indexOfCell(cell: View): Int = cells.indexOf(cell)

    /** Animate every cell (except an optionally-dragged one) to its slot. */
    fun applySlots(animate: Boolean, except: View? = null) {
        cells.forEachIndexed { index, cell ->
            if (cell === except) return@forEachIndexed
            if (animate) {
                springTo(cell, SpringAnimation.TRANSLATION_X, slotX(index))
                springTo(cell, SpringAnimation.TRANSLATION_Y, slotY(index))
            } else {
                cell.translationX = slotX(index)
                cell.translationY = slotY(index)
            }
        }
    }

    private fun springTo(view: View, prop: androidx.dynamicanimation.animation.DynamicAnimation.ViewProperty, target: Float) {
        SpringAnimation(view, prop).apply {
            spring = SpringForce(target).apply {
                stiffness = SpringForce.STIFFNESS_MEDIUM
                dampingRatio = SpringForce.DAMPING_RATIO_LOW_BOUNCY
            }
            start()
        }
    }

    // ── model mutation (called by DragController) ───────────────────────────────

    /** Move a cell+item from one index to another and reflow. */
    fun moveCell(from: Int, to: Int) {
        if (from == to || from !in items.indices) return
        val clampedTo = to.coerceIn(0, items.size - 1)
        items.add(clampedTo, items.removeAt(from))
        cells.add(clampedTo, cells.removeAt(from))
        applySlots(animate = true, except = cells[clampedTo])
    }

    /** Merge the app at [sourceIndex] into the app/folder at [targetIndex]. */
    fun mergeIntoFolder(sourceIndex: Int, targetIndex: Int) {
        if (sourceIndex !in items.indices || targetIndex !in items.indices) return
        val source = items[sourceIndex]
        val target = items[targetIndex]
        if (source !is HomeItem.App) return

        val repo = RuOSApp.instance.appRepository
        when (target) {
            is HomeItem.Folder -> target.apps.add(source.info)
            is HomeItem.App -> {
                val folder = HomeItem.Folder(
                    repo.newFolderId(),
                    context.getString(R.string.folder_default_name),
                    mutableListOf(target.info, source.info)
                )
                items[targetIndex] = folder
            }
        }
        items.removeAt(sourceIndex)
        // Rebuild views to reflect the new folder / removed app.
        setItems(items.toList())
        commit()
    }

    /** Persist the current layout and let the host know. */
    fun commit() {
        RuOSApp.instance.appRepository.saveHomeLayout(allLayoutItemsFromHost())
        onLayoutCommitted?.invoke()
    }

    /**
     * The repository persists the WHOLE home layout, but a page only knows its own
     * slice. The pager owns reassembly; it sets this supplier so commit() can write
     * the full list. Defaults to this page's items when unset (single-page case).
     */
    var allLayoutItemsSupplier: (() -> List<HomeItem>)? = null
    private fun allLayoutItemsFromHost(): List<HomeItem> =
        allLayoutItemsSupplier?.invoke() ?: items.toList()

    // ── touch (jiggle drag) ─────────────────────────────────────────────────────

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        // Always let the drag controller observe DOWN coords (so a pickup after
        // jiggle turns on mid-gesture targets the originally-pressed icon), but
        // only actually steal the stream once we're in jiggle mode.
        val wants = drag.onInterceptTouch(ev)
        return jiggle && wants
    }

    override fun onTouchEvent(event: MotionEvent): Boolean =
        if (jiggle) drag.onTouch(event) else super.onTouchEvent(event)
}
