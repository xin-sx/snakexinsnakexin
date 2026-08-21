package com.demo.snakexin

import android.content.Context
import android.graphics.Matrix
import android.graphics.PointF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView

/**
 * 支持双指缩放 / 单指拖动 / 双击切换缩放级别的 ImageView。
 * 用于备注照片全屏查看。
 */
class ZoomableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private val matrixCurrent = Matrix()
    private val matrixValues = FloatArray(9)

    private var minScale = 1f
    private var maxScale = 5f
    private var midScale = 2f

    private val last = PointF()
    private var startX = 0f
    private var startY = 0f
    private var mode = NONE
    private var isImageSet = false

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val scaleFactor = detector.scaleFactor
            val current = currentScale()
            var next = current * scaleFactor
            if (next < minScale) next = minScale
            if (next > maxScale) next = maxScale
            val realFactor = next / current
            matrixCurrent.postScale(realFactor, realFactor, detector.focusX, detector.focusY)
            imageMatrix = matrixCurrent
            constrainPan()
            return true
        }
    })

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            val current = currentScale()
            val target = if (current >= midScale - 0.05f) minScale else midScale
            val factor = target / current
            matrixCurrent.postScale(factor, factor, e.x, e.y)
            imageMatrix = matrixCurrent
            constrainPan()
            return true
        }
    })

    init {
        scaleType = ScaleType.MATRIX
    }

    override fun setImageMatrix(matrix: Matrix?) {
        super.setImageMatrix(matrix)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (changed && isImageSet) {
            fitCenterMatrix()
        }
    }

    /** 设置图片后，自动按 FIT_CENTER 计算初始 matrix。 */
    override fun setImageDrawable(drawable: android.graphics.drawable.Drawable?) {
        super.setImageDrawable(drawable)
        isImageSet = drawable != null
        post { fitCenterMatrix() }
    }

    private fun fitCenterMatrix() {
        val d = drawable ?: return
        val viewW = (width - paddingLeft - paddingRight).toFloat()
        val viewH = (height - paddingTop - paddingBottom).toFloat()
        if (viewW <= 0f || viewH <= 0f) return
        val dw = d.intrinsicWidth.toFloat()
        val dh = d.intrinsicHeight.toFloat()
        if (dw <= 0f || dh <= 0f) return
        val scale = minOf(viewW / dw, viewH / dh)
        val tx = (viewW - dw * scale) / 2f
        val ty = (viewH - dh * scale) / 2f
        matrixCurrent.reset()
        matrixCurrent.postScale(scale, scale)
        matrixCurrent.postTranslate(tx, ty)
        imageMatrix = matrixCurrent
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                last.set(event.x, event.y)
                startX = event.x
                startY = event.y
                mode = DRAG
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                last.set(event.x, event.y)
                mode = ZOOM
            }
            MotionEvent.ACTION_MOVE -> {
                if (mode == DRAG && currentScale() > minScale + 0.001f) {
                    val dx = event.x - last.x
                    val dy = event.y - last.y
                    matrixCurrent.postTranslate(dx, dy)
                    imageMatrix = matrixCurrent
                    constrainPan()
                }
                last.set(event.x, event.y)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                mode = NONE
            }
        }
        parent?.requestDisallowInterceptTouchEvent(currentScale() > minScale + 0.001f || mode == ZOOM)
        return true
    }

    private fun currentScale(): Float {
        matrixCurrent.getValues(matrixValues)
        return matrixValues[Matrix.MSCALE_X]
    }

    /** 限制平移范围，确保图片始终覆盖视图。 */
    private fun constrainPan() {
        val d = drawable ?: return
        matrixCurrent.getValues(matrixValues)
        val scale = matrixValues[Matrix.MSCALE_X]
        val tx = matrixValues[Matrix.MTRANS_X]
        val ty = matrixValues[Matrix.MTRANS_Y]

        val viewW = (width - paddingLeft - paddingRight).toFloat()
        val viewH = (height - paddingTop - paddingBottom).toFloat()
        val imgW = d.intrinsicWidth * scale
        val imgH = d.intrinsicHeight * scale

        var newTx = tx
        var newTy = ty
        if (imgW <= viewW) {
            newTx = (viewW - imgW) / 2f
        } else {
            val minTx = viewW - imgW
            val maxTx = 0f
            if (tx > maxTx) newTx = maxTx
            if (tx < minTx) newTx = minTx
        }
        if (imgH <= viewH) {
            newTy = (viewH - imgH) / 2f
        } else {
            val minTy = viewH - imgH
            val maxTy = 0f
            if (ty > maxTy) newTy = maxTy
            if (ty < minTy) newTy = minTy
        }
        if (newTx != tx || newTy != ty) {
            matrixCurrent.postTranslate(newTx - tx, newTy - ty)
            imageMatrix = matrixCurrent
        }
    }

    companion object {
        private const val NONE = 0
        private const val DRAG = 1
        private const val ZOOM = 2
    }
}
