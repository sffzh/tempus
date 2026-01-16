package com.cappielloantonio.tempo.ui.fragment.view
import android.R
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import com.cappielloantonio.tempo.ui.fragment.model.BilingualLine
import com.google.android.material.color.MaterialColors
import kotlin.math.max
import kotlin.math.min

class LyricView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // 歌词数据
    private var lyrics: List<BilingualLine> = emptyList()

    // 当前时间对应的行索引
    private var currentLineIndex: Int = 0

    // 当前滚动偏移（以“行”为单位的偏移量 * lineHeight）
    private var currentOffsetY: Float = 0f

    // 行高（像素）
    private var lineHeight: Float = 0f

    private fun themeColor(attr: Int): Int {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(attr, typedValue, true)
        return typedValue.data
    }

    private val normalPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
        textSize = sp(18f)
        textAlign = Paint.Align.CENTER
    }

    private val highlightPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = themeColor(com.google.android.material.R.attr.colorOnBackground)
        textSize = sp(22f)
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val secondaryPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant) // 更淡
        textSize = sp(16f)
        textAlign = Paint.Align.CENTER
    }

    private val secondaryHightlightPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = themeColor(com.google.android.material.R.attr.colorOnBackground) // 更淡
        textSize = sp(16f)
        textAlign = Paint.Align.CENTER
    }

    // 点击回调
    private var onLineClickListener: ((timeMs: Long, index: Int) -> Unit)? = null

    // 用于测量文字高度
    private val textBounds = android.graphics.Rect()



    init {
        isClickable = true
        isFocusable = true
        // 预估行高
        val sample = "载入歌词……"
        highlightPaint.getTextBounds(sample, 0, sample.length, textBounds)
        lineHeight = textBounds.height() * 1.8f
    }

    private data class LyricLayout(
        val primary: StaticLayout,
        val primaryHighlight: StaticLayout,
        val secondary: StaticLayout?,
        val secondaryHighlight: StaticLayout?,
        val height: Int,
        var heightFix: Int
    )

    private var layouts: List<LyricLayout> = emptyList()

    private lateinit var cumulativeHeights: IntArray

    private fun buildLayout(text: String, paint: TextPaint, width: Int): StaticLayout {
        return StaticLayout.Builder
            .obtain(text, 0, text.length, paint, width)
//            .setAlignment(Layout.Alignment.ALIGN_CENTER)//原因不明，AI也无法回答，设置了居中后反而会变成右对齐。
            .setIncludePad(false)
            .build()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        android.util.Log.d("LyricsDebug", "onSizeChanged: width=$w, height=$h, oldWidth=$oldw, oldHeight=$oldh")
        super.onSizeChanged(w, h, oldw, oldh)
        if (lyrics.isNotEmpty()) {
            // 重新构建 layouts，复用 setLyrics 的逻辑
            setLyrics(lyrics)
        }
    }

    fun setLyrics(list: List<BilingualLine>) {
        lyrics = list.sortedBy { it.timeMs }
        post { rebuildLayouts() }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        // 重新应用主题颜色
        normalPaint.color = themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
        highlightPaint.color = themeColor(com.google.android.material.R.attr.colorOnBackground)
        secondaryPaint.color = themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
        secondaryHightlightPaint.color = themeColor(com.google.android.material.R.attr.colorOnBackground)

        // 重新构建 layout（字体颜色变化不会自动刷新）
        rebuildLayouts()
    }


    // 主副歌词之间的垂直间距（像素）
    private val primarySecondaryGap = dp(8).toInt()
    // 歌词行间距
    private val lineGap = dp(12).toInt()

    private fun rebuildLayouts(){
        if (width <= 0) return
        val availableWidth = ((width - paddingLeft - paddingRight)*0.87f).toInt().coerceAtLeast(1)
        layouts = lyrics.map { line ->
            val primaryLayout = buildLayout(line.primary, normalPaint, availableWidth)

            val primaryHighlight = buildLayout(line.primary, highlightPaint, availableWidth)

            val secondaryLayout = line.secondary?.let {
                buildLayout(it, secondaryPaint, availableWidth)
            }
            val secondaryHighlight = line.secondary?.let {
                buildLayout(it, secondaryHightlightPaint, availableWidth)
            }

            val totalHeight = primaryLayout.height +
                    (secondaryLayout?.height?.plus(primarySecondaryGap) ?: 0) + // 主副歌词之间的间距
                    lineGap

            LyricLayout(primaryLayout, primaryHighlight, secondaryLayout, secondaryHighlight
                , totalHeight
                , (primaryHighlight.height * 1.15f - primaryLayout.height).toInt())
        }

        // 计算累计高度
        cumulativeHeights = IntArray(layouts.size)
        var sum = 0
        for (i in layouts.indices) {
            cumulativeHeights[i] = sum
            sum += layouts[i].height
        }

        invalidate()
    }


    private fun getOffsetForLine(index: Int, progress: Float): Float {
        val base = cumulativeHeights[index]
        val next = cumulativeHeights.getOrNull(index + 1) ?: (base + layouts[index].height)
        return base + (next - base) * progress
    }

    fun setOnLineClickListener(listener: ((timeMs: Long, index: Int) -> Unit)?) {
        onLineClickListener = listener
    }

    private fun lerpColor(startColor: Int, endColor: Int, t: Float): Int {
        val sA = (startColor shr 24) and 0xff
        val sR = (startColor shr 16) and 0xff
        val sG = (startColor shr 8) and 0xff
        val sB = startColor and 0xff

        val eA = (endColor shr 24) and 0xff
        val eR = (endColor shr 16) and 0xff
        val eG = (endColor shr 8) and 0xff
        val eB = endColor and 0xff

        val a = (sA + ((eA - sA) * t)).toInt()
        val r = (sR + ((eR - sR) * t)).toInt()
        val g = (sG + ((eG - sG) * t)).toInt()
        val b = (sB + ((eB - sB) * t)).toInt()

        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    private val maxScale = 1.15f

    /**
     * 外部播放器定期调用，positionMs 为当前播放进度（毫秒）
     */
    fun setProgress(positionMs: Long) {
        if (lyrics.isEmpty() || layouts.isEmpty()) return

        val index = findCurrentLineIndex(positionMs)
        currentLineIndex = index

        val current = lyrics[index]
        val next = lyrics.getOrNull(index + 1)

        val start = current.timeMs
        val end = next?.timeMs ?: (start + 4000)
        val duration = max(1L, end - start)

        val progress = ((positionMs - start).toFloat() / duration).coerceIn(0f, 1f)

        // ★ 新增：高亮动画进度
        highlightProgress = progress

        currentOffsetY = getOffsetForLine(index, progress)

        invalidate()
    }

    // 当前行动画进度（0f = 普通行，1f = 完全高亮）
    private var highlightProgress = 1f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (lyrics.isEmpty() || layouts.isEmpty()) return

        val centerY = height / 2f
        val centerOffset = currentOffsetY

//        val availableWidth = (width - paddingLeft - paddingRight).coerceAtLeast(1)
        var lastHeightFix = 0
        for (i in layouts.indices) {
            val layout = layouts[i]
            val top = cumulativeHeights[i] - centerOffset + centerY

            // 只绘制可见区域
            if (top > height || top + layout.height < 0) continue

            val  scale: Float
            val primaryLayout: StaticLayout
            val sencondLayout: StaticLayout?
            val primaryTop :Float
            val blendedColor : Int
            // 是否是当前行
            if(i == currentLineIndex){
                // 计算缩放比例（1.0 ~ 1.15）
                scale =   1.0f + (maxScale - 1f) * highlightProgress
                blendedColor = lerpColor(normalPaint.color, highlightPaint.color, highlightProgress)
                // 主歌词：选择高亮或普通 layout
                primaryLayout = layout.primaryHighlight
                sencondLayout = layout.secondaryHighlight
                lastHeightFix = layout.heightFix
                primaryTop = top

            }else{
                scale = 1.0f
                blendedColor = normalPaint.color
                primaryLayout = layout.primary
                sencondLayout = layout.secondary
                primaryTop = top + lastHeightFix

            }

            // 主歌词居中
            canvas.save()
            canvas.translate(width/2f, primaryTop)
            canvas.scale(scale, scale)
            primaryLayout.paint.color = blendedColor
            primaryLayout.draw(canvas)
            canvas.restore()

            // 副歌词居中
            sencondLayout?.let { sec ->
//                val secondaryLeft = paddingLeft + (width - paddingLeft - paddingRight - sec.width) / 2f
                val secTop = top + lastHeightFix + layout.primary.height + primarySecondaryGap

                canvas.save()
                canvas.translate(width/2f, secTop)
                sec.draw(canvas)
                canvas.restore()
            }
        }
        drawFadeMask(canvas)
    }

    private fun getBackgroundColor(): Int {
        val drawable = background ?: return Color.BLACK

        return when (drawable) {
            is ColorDrawable -> drawable.color
            is GradientDrawable -> {
                // 如果是 shape/gradient，尝试读取 solidColor
                drawable.color?.defaultColor ?: Color.BLACK
            }
            else -> Color.BLACK
        }
    }

    private val fadePaint = Paint()
    /**
     * 淡入淡出遮罩
     * 现在还有边框和背景没有处理，这遮罩还不如不要
     */
    private fun drawFadeMask(canvas: Canvas) {
        val fadeHeight = dp(48).toInt()
        val bgColor = getBackgroundColor()

        // 顶部渐变：背景色 → 透明
        val topShader = LinearGradient(
            0f, 0f, 0f, fadeHeight.toFloat(),
            bgColor, bgColor and 0x00FFFFFF,
            Shader.TileMode.CLAMP
        )
        fadePaint.shader = topShader
        fadePaint.xfermode = null
        canvas.drawRect(0f, 0f, width.toFloat(), fadeHeight.toFloat(), fadePaint)

        // 底部渐变：透明 → 背景色
        val bottomShader = LinearGradient(
            0f, height - fadeHeight.toFloat(), 0f, height.toFloat(),
            bgColor and 0x00FFFFFF, bgColor,
            Shader.TileMode.CLAMP
        )
        fadePaint.shader = bottomShader
        canvas.drawRect(0f, height - fadeHeight.toFloat(), width.toFloat(), height.toFloat(), fadePaint)
    }



    private var lastClickTime = 0L
    private val clickCooldown = 300L // 300ms 内不允许再次点击

    /**
     * 点击歌词，跳转到对应时间
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
//        android.util.Log.d("LyricsDebug", "Touch: x=${event.x}, y=${event.y}")
        if (lyrics.isEmpty() || layouts.isEmpty()) return super.onTouchEvent(event)

        when (event.action) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_UP -> {
                // 点击节流：忽略短时间内的重复点击
                val now = System.currentTimeMillis()
                if (now - lastClickTime < clickCooldown) {
                    return true // 忽略本次点击
                }
                lastClickTime = now


                val centerY = height / 2f
                // 计算点击相对于 cumulative 原点的像素位置
                val absoluteY = currentOffsetY + (event.y - centerY)
                // 二分查找或线性查找找到被点击的行
                val clickedIndex = findIndexByY(absoluteY)

                if (clickedIndex in lyrics.indices) {
                    onLineClickListener?.invoke(lyrics[clickedIndex].timeMs, clickedIndex)
                }
                performClick() // 遵循 A11y 规范
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    // 根据像素 y（相对于 cumulative 起点）查找行索引
    private fun findIndexByY(y: Float): Int {
        if (!::cumulativeHeights.isInitialized || layouts.isEmpty()) return 0
        var left = 0
        var right = layouts.size - 1
        val yy = y.toInt()

        while (left <= right) {
            val mid = (left + right) / 2
            val start = cumulativeHeights[mid]
            val end = start + layouts[mid].height
            if (yy in start until end) {
                return mid
            } else if (yy < start) {
                right = mid - 1
            } else {
                left = mid + 1
            }
        }
        // 如果没有命中，返回最接近的索引
        return  min(max(left, 0), layouts.size - 1)
    }

    private fun findCurrentLineIndex(positionMs: Long): Int {
        var left = 0
        var right = lyrics.size - 1
        var result = 0

        while (left <= right) {
            val mid = (left + right) / 2
            val time = lyrics[mid].timeMs
            if (time <= positionMs) {
                result = mid
                left = mid + 1
            } else {
                right = mid - 1
            }
        }
        return result
    }

    private fun sp(value: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            value,
            resources.displayMetrics
        )
    }

    private fun dp(value: Int): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        )
    }
}