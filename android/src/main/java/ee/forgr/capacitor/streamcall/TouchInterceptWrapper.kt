import android.view.MotionEvent
import android.view.ViewGroup
import androidx.coordinatorlayout.widget.CoordinatorLayout

class TouchInterceptWrapper(private val originalViewGroup: ViewGroup) : CoordinatorLayout(
    originalViewGroup.context
) {
    init {
        // Copy layout parameters and children
        layoutParams = originalViewGroup.layoutParams
        while (originalViewGroup.childCount > 0) {
            val child = originalViewGroup.getChildAt(0)
            originalViewGroup.removeViewAt(0)
            addView(child)
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        // Traverse children from top (highest z) to bottom
        for (i in childCount - 1 downTo 0) {
            val child = getChildAt(i)
            if (child.visibility == VISIBLE) {
                val copy = MotionEvent.obtain(ev)
                val handled = child.dispatchTouchEvent(copy)
                copy.recycle()
                if (handled) return true
            }
        }
        return false
    }
}
