package com.alivehealth.alive.ui

import android.content.Context
import android.util.AttributeSet
import androidx.cardview.widget.CardView

class SquareCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : CardView(context, attrs, defStyleAttr) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // 使用宽度的测量规格作为高度的测量规格，确保宽度和高度相等
        super.onMeasure(widthMeasureSpec, widthMeasureSpec)
    }
}