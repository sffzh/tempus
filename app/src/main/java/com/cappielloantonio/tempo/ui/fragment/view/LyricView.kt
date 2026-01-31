package com.cappielloantonio.tempo.ui.fragment.view
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import com.google.android.material.color.MaterialColors
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.cappielloantonio.tempo.R
import com.cappielloantonio.tempo.ui.fragment.model.BilingualLine
import kotlin.math.max
import kotlin.math.min


class LyricView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object{
        const val TAG: String = "LyricView"
    }

    // 歌词数据
    private var lyrics: List<BilingualLine> = emptyList()

    // 当前时间对应的行索引
    private var currentLineIndex: Int = 0

    // 当前滚动偏移（以“行”为单位的偏移量 * lineHeight）
    private var currentOffsetY: Float = 0f
    //动画开始的起点，使用初始currentOffsetY 赋值，与currentOffsetY对比计算动画进度。
    private var startOffset: Float = 0f
    private var targetOffset: Float = 0f

    // ===== 渐变遮罩缓存（零 GC） =====
    private var fadeHeightPx = 0
    private var topFadeShader: LinearGradient? = null
    private var bottomFadeShader: LinearGradient? = null
    private val fadePaint = Paint()

    private fun themeColor(attr: Int): Int {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(attr, typedValue, true)
        return typedValue.data
    }

    private val normalPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        // 第三个参数是默认色，防止主题中未定义该属性时崩溃
        color = MaterialColors.getColor(context, R.attr.colorOnSurfaceVariant, Color.GRAY)
        textSize = sp(20f)
        textAlign = Paint.Align.CENTER
    }

    private val highlightPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MaterialColors.getColor(context, R.attr.colorOnBackground, Color.GRAY) // 更淡
        textSize = sp(20f)
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val secondaryPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MaterialColors.getColor(context, R.attr.colorOnSurfaceVariant, Color.GRAY)
        textSize = sp(18f)
        textAlign = Paint.Align.CENTER
    }

    private val secondaryHightlightPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MaterialColors.getColor(context, R.attr.colorOnBackground, Color.GRAY) // 更淡
        textSize = sp(19f)
        textAlign = Paint.Align.CENTER
    }

    // 点击回调
    private var onLineClickListener: ((timeMs: Long, index: Int) -> Unit)? = null

    private var primaryLineHeight = 0
    private var primaryHighlightLineHeight = 0
    private var secondaryLineHeight = 0
    private var secondaryHighlightLineHeight = 0
    private fun computeLineHeights() {
        val pfm = normalPaint.fontMetrics
        primaryLineHeight = (pfm.descent - pfm.ascent).toInt()

        val phfm = highlightPaint.fontMetrics
        primaryHighlightLineHeight = (phfm.descent - phfm.ascent).toInt()

        val sfm = secondaryPaint.fontMetrics
        secondaryLineHeight = (sfm.descent - sfm.ascent).toInt()

        val shfm = secondaryHightlightPaint.fontMetrics
        secondaryHighlightLineHeight = (shfm.descent - shfm.ascent).toInt()
    }

    init {
        Log.d(TAG, "LyricView Started")
        isClickable = true
        isFocusable = true
        // 预估行高
        computeLineHeights()
    }

    private data class LyricLayout(
        val primary: StaticLayout,
        val primaryHighlight: StaticLayout,
        val secondary: StaticLayout?,
        val secondaryHighlight: StaticLayout?,
        val height: Int,
        val primaryBaselineOffset: Float,
        var hightlightHeight: Float
    )

    private var layouts: List<LyricLayout> = emptyList()

    private lateinit var cumulativeHeights: IntArray

    private fun buildLayout(text: String, paint: TextPaint, width: Int): StaticLayout {
        val key = "$text|${paint.textSize}|${paint.color}|$width"
        layoutCache[key]?.let { return it }

        val layout = StaticLayout.Builder
            .obtain(text, 0, text.length, paint, width)
            /*.setAlignment(Layout.Alignment.ALIGN_CENTER)//不能设置居中对齐。原因暂不明，设置了居中后反而会变成右对齐。*/
            .setIncludePad(false)
            .build()
        layoutCache[key] = layout
        return layout
    }

    // ===== StaticLayout LRU 缓存 =====
    private val layoutCache = object : LinkedHashMap<String, StaticLayout>(200, 0.75f, true) {
        private val maxSize = 400   // 200~400 都很安全

        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, StaticLayout>?): Boolean {
            return size > maxSize
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
//        Log.d("LyricsDebug", "onSizeChanged: width=$w, height=$h, oldWidth=$oldw, oldHeight=$oldh")
        super.onSizeChanged(w, h, oldw, oldh)
        computeLineHeights()
        fadeHeightPx = dp(55).toInt()

        // 推荐：直接通过属性获取颜色，不再依赖 getBackgroundColor() 里的 Drawable 判断
        val bgColor = MaterialColors.getColor(context, R.attr.colorSurface, Color.BLACK)
        Log.d(TAG, "theme_bgColor = ${Integer.toHexString(bgColor)}")

        val transparent = bgColor and 0x00FFFFFF

        // 顶部渐变：背景色 → 透明
        topFadeShader = LinearGradient(
            0f, 0f, 0f, fadeHeightPx.toFloat(),
            bgColor, transparent,
            Shader.TileMode.CLAMP
        )

        // 底部渐变：透明 → 背景色
        bottomFadeShader = LinearGradient(
            0f, (h - fadeHeightPx).toFloat(), 0f, h.toFloat(),
            transparent, bgColor,
            Shader.TileMode.CLAMP
        )

        if (lyrics.isNotEmpty()) {
            // 重新构建 layouts，复用 setLyrics 的逻辑
            setLyrics(lyrics)
        }
        if (w != oldw) {
            //宽度变化清空layout缓存
            layoutCache.clear()
            rebuildLayouts()
        }
        lyricOffsetNeedFix =true
    }

    fun setLyrics(list: List<BilingualLine>) {
        lyrics = list.sortedBy { it.timeMs }
        post { rebuildLayouts() }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        // 重新应用主题颜色
        normalPaint.color = MaterialColors.getColor(context,R.attr.colorOnSurfaceVariant, Color.GRAY)
        highlightPaint.color = MaterialColors.getColor(context, R.attr.colorOnBackground, Color.GRAY)
        secondaryPaint.color = MaterialColors.getColor(context, R.attr.colorOnSurfaceVariant, Color.GRAY)
        secondaryHightlightPaint.color = MaterialColors.getColor(context, R.attr.colorOnBackground, Color.GRAY)

        // 重新构建 layout（字体颜色变化不会自动刷新）
        layoutCache.clear() // ⭐必须清空缓存，否则颜色不更新
        rebuildLayouts()
    }


    // 主副歌词之间的垂直间距（像素）
    private val primarySecondaryGap = dp(8).toInt()
    // 歌词行间距
    private val lineGap = dp(12).toInt()

    private fun rebuildLayouts(){
        if (width <= 0) return
        val availableWidth = ((width - paddingLeft - paddingRight)*0.87f).toInt().coerceAtLeast(1)
        val newLayouts  = lyrics.map { line ->
            val primaryLayout = buildLayout(line.primary, normalPaint, availableWidth)

            val primaryHighlight = buildLayout(line.primary, highlightPaint, availableWidth)

            val secondaryLayout = line.secondary?.let {
                buildLayout(it, secondaryPaint, availableWidth)
            }
            val secondaryHighlight = line.secondary?.let {
                buildLayout(it, secondaryHightlightPaint, availableWidth)
            }

            val totalHeight = primaryLineHeight * primaryLayout.lineCount +
                    (secondaryLayout?.lineCount?.times(secondaryLineHeight)?.plus(primarySecondaryGap)
                        ?: 0) +
                    lineGap // 主副歌词之间的间距
            val primaryHighlightHeight = primaryHighlightLineHeight * primaryLayout.lineCount * maxScale;
            val hightlightHeight = primaryHighlightHeight +
                    (secondaryHighlight?.lineCount?.times(secondaryHighlightLineHeight)?.plus(primarySecondaryGap) ?: 0) +
                    lineGap

//            Log.d(TAG, "rebuild_layouts: primaryLineHeight: $primaryLineHeight, secondaryLineHeight: " +
//                    "${secondaryLayout?.lineCount}; primaryLayout.lineCount: ${primaryLayout.lineCount}, totalHeight: $totalHeight; lineText:${line.primary}")

            LyricLayout(primaryLayout, primaryHighlight, secondaryLayout, secondaryHighlight
                , totalHeight
                , primaryHighlightHeight/2f
                , hightlightHeight)

        }

        // 计算累计高度
//        cumulativeHeights = IntArray(layouts.size)
        val newHeights = IntArray(newLayouts.size)
        var sum = 0
        for (i in newLayouts.indices) {
            newHeights[i] = sum + (newLayouts[i].primaryBaselineOffset).toInt()
            sum += newLayouts[i].height
        }

        // 3. 原子性赋值：确保两者同步更新
        layouts = newLayouts
        cumulativeHeights = newHeights

        invalidate()
    }


    fun setOnLineClickListener(listener: ((timeMs: Long, index: Int) -> Unit)?) {
        onLineClickListener = listener
    }

    private val maxScale = 1.15f

    // 当前行放大进度（0~1）
    private var highlightProgress = 0f

    // 上一行缩小进度（0~1）
    private var prevHighlightProgress = 0f

    // 上一行 index
    private var previousLineIndex = -1


    var lyricOffsetNeedFix = true


    private val offsetProperty = object : FloatPropertyCompat<LyricView>("offset") {
        override fun getValue(view: LyricView): Float {
            return view.currentOffsetY
        }

        override fun setValue(view: LyricView, value: Float) {
            view.currentOffsetY = value

            // ⭐ 计算动画进度（0~1）
            val total = targetOffset - startOffset
            if (total != 0f) {
                val t = ((value - startOffset) / total).coerceIn(0f, 1f)
                highlightProgress = t
                prevHighlightProgress = 1f - t
            }

            view.invalidate()
        }
    }


    private val offsetAnim = SpringAnimation(this, offsetProperty).apply {
        spring = SpringForce().apply {
            stiffness = SpringForce.STIFFNESS_LOW              // 刚度：越低越“软”
            dampingRatio = SpringForce.DAMPING_RATIO_LOW_BOUNCY // 阻尼：控制回弹感
        }
    }


    /**
     * 外部播放器定期调用，positionMs 为当前播放进度（毫秒）
     */
    fun setProgress(positionMs: Long) {
        if (lyrics.isEmpty() || !::cumulativeHeights.isInitialized || layouts.size != cumulativeHeights.size) return
//        if (lyrics.isEmpty() || layouts.isEmpty()) return

        val index = findCurrentLineIndex(positionMs)
        val needMove = if (index == currentLineIndex) {
            false
        }else{
            previousLineIndex = currentLineIndex
            true
        }
        currentLineIndex = index

        val toOffset = cumulativeHeights[index].toFloat()

        if (!lyricOffsetNeedFix && (highlightProgress != 1f || !needMove)){
            // 有其他动画进程正在运行，本次不运行动画
            // 未进入下一行，不需要运行动画
            return
        }
        lyricOffsetNeedFix = false


        if (currentOffsetY == toOffset){
//            需要渲染画面，但不需要动画
            invalidate()
        }else{
            offsetAnim.cancel()

            startOffset = currentOffsetY
            targetOffset = toOffset
            val distance = toOffset - currentOffsetY
            val velocity = distance / 120f   // 120ms 的速度常数，可调
            offsetAnim.setStartVelocity(velocity)
            offsetAnim.setStartValue(startOffset)
            offsetAnim.animateToFinalPosition(toOffset)

        }

    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (lyrics.isEmpty() || layouts.isEmpty() || !::cumulativeHeights.isInitialized) return
//        if (lyrics.isEmpty() || layouts.isEmpty()) return

        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        val centerX = viewWidth / 2f
        val centerY = viewHeight / 2f
        val centerOffset = currentOffsetY

        var lastHeightFix = 0f

        val lineCount = layouts.size
        var i = 0
        while (i < lineCount) {
            val layout = layouts[i]

            // 该行顶部（未考虑 heightFix）
            val top = cumulativeHeights[i] - centerOffset + centerY

            // 可见性裁剪：整行在屏幕外则跳过
            if (top > viewHeight || top + layout.height < 0f) {
                i++
                continue
            }

            // 计算缩放比例 & 使用哪个 layout
            val isCurrent = (i == currentLineIndex)
            val isPrevious = (i == previousLineIndex)

            val scale: Float
            val primaryLayout: StaticLayout
            val secondaryLayout: StaticLayout?
            val primaryTop: Float

            if (isCurrent) {
                // 当前行：放大
                scale = 1.0f + (maxScale - 1f) * highlightProgress
                primaryLayout = layout.primaryHighlight
                secondaryLayout = layout.secondaryHighlight
                lastHeightFix = layout.hightlightHeight - layout.height
                primaryTop = top
            } else {
                // 非当前行
                scale = if (isPrevious) {
                    1.0f + (maxScale - 1f) * prevHighlightProgress
                } else {
                    1.0f
                }
                primaryLayout = layout.primary
                secondaryLayout = layout.secondary
                primaryTop = top + lastHeightFix
            }

            // ===== 绘制主歌词（带缩放） =====
//            val primaryWidth = primaryLayout.width.toFloat()
            val primaryHeight = primaryLayout.height.toFloat()

            // 行中心作为缩放中心
            canvas.save()

            // 1. 移动到缩放中心
            canvas.translate(centerX, primaryTop)
            // 2. 缩放
            if (scale != 1.0f) {
                canvas.scale(scale, scale)
            }
            // 3. 把原点移到 layout 左上角（居中）
            // 画布向上偏移歌词高度的一半。注意偏移的高度同样会放大，所以不要用放大后的歌词高度进行偏移量计算。
            canvas.translate(0f, -layout.primaryBaselineOffset)

            // 4. 从 (0,0) 开始绘制主歌词
            primaryLayout.draw(canvas)

            canvas.restore()

            // ===== 绘制副歌词（不缩放，跟随主行位置） =====
            if (secondaryLayout != null) {
//                val sfm = secondaryLayout.paint.fontMetrics
//                val secBaselineOffset = -sfm.ascent

                val secTop = primaryTop + primaryHeight + primarySecondaryGap

                canvas.save()
                canvas.translate(centerX, secTop -layout.primaryBaselineOffset)
                secondaryLayout.draw(canvas)
                canvas.restore()
            }

            i++
        }

        drawFadeMask(canvas)
    }

    /**
     * 淡入淡出遮罩
     * 现在还有边框和背景没有处理，这遮罩还不如不要
     */
    private fun drawFadeMask(canvas: Canvas) {
        val topShader = topFadeShader ?: return
        val bottomShader = bottomFadeShader ?: return

        fadePaint.shader = topShader
        canvas.drawRect(0f, 0f, width.toFloat(), fadeHeightPx.toFloat(), fadePaint)

        fadePaint.shader = bottomShader
        canvas.drawRect(
            0f,
            (height - fadeHeightPx).toFloat(),
            width.toFloat(),
            height.toFloat(),
            fadePaint
        )
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
                    lyricOffsetNeedFix = true
                    onLineClickListener?.invoke(lyrics[clickedIndex].timeMs, clickedIndex)
                }
                performClick() // 遵循 A11y 规范
                return true
            }
        }
        return super.onTouchEvent(event)
    }
    override fun performClick(): Boolean {
        return super.performClick()
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

    /**
     * 根据时间戳查找当前行的index.查找时将时间戳加100ms,即提前100ms进入下一行。
     */
    private fun findCurrentLineIndex(positionMs: Long): Int {
        val checkTMs = positionMs + 100
        var left = 0
        var right = lyrics.size - 1
        var result = 0

        while (left <= right) {
            val mid = (left + right) / 2
            if (lyrics[mid].timeMs <= checkTMs) {
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