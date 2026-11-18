package com.selfgit.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

/** 越界拉伸的位移上限（阻尼前），超过后基本拉不动。 */
private val DEFAULT_MAX_PULL = 132.dp

/** 拖动系数：手指移动 1px，内容约位移 0.55px 起，越拉越沉。 */
private const val DRAG_FACTOR = 0.55f

/** 最大拉伸幅度（相对页面高度的比例），保持「略微」的手感。 */
private const val MAX_STRETCH = 0.05f

/**
 * 拉伸回弹控制器：记录当前越界量，松手后按弹簧回 0。
 */
private class ElasticState(
    private val scope: CoroutineScope,
    private val maxPull: Float
) {
    var pull by mutableFloatStateOf(0f)
        private set

    private var settleJob: Job? = null

    fun stopSettle() {
        if (settleJob?.isActive == true) {
            settleJob?.cancel()
            settleJob = null
        }
    }

    fun dragBy(delta: Float): Float {
        val headroom = ((maxPull - abs(pull)) / maxPull).coerceIn(0.04f, 1f)
        val step = delta * DRAG_FACTOR * headroom
        val next = (pull + step).coerceIn(-maxPull, maxPull)
        val consumed = next - pull
        pull = next
        return consumed
    }

    fun unwindBy(delta: Float): Float {
        val next = (pull + delta).let { value ->
            if (pull > 0f) value.coerceAtLeast(0f) else value.coerceAtMost(0f)
        }
        val consumed = next - pull
        pull = next
        return consumed
    }

    fun settle() {
        if (pull == 0f) return
        if (settleJob?.isActive == true) return
        settleJob = scope.launch {
            animate(
                initialValue = pull,
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) { value, _ -> pull = value }
        }
    }
}

/**
 * 给纵向滚动容器加「越界拉伸 + 阻尼回弹」：
 * `Modifier.elasticOverscroll(scrollState).verticalScroll(scrollState)`。
 */
fun Modifier.elasticOverscroll(
    scrollState: ScrollState,
    maxPull: Dp = DEFAULT_MAX_PULL
): Modifier = composed {
    val density = LocalDensity.current
    val maxPx = with(density) { maxPull.toPx() }.coerceAtLeast(1f)
    val scope = rememberCoroutineScope()
    val elastic = remember(scrollState, maxPx) { ElasticState(scope, maxPx) }

    val connection = remember(elastic, scrollState) {
        object : NestedScrollConnection {

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                val dy = available.y
                if (dy == 0f) return Offset.Zero

                elastic.stopSettle()

                if (elastic.pull != 0f) {
                    val consumed = elastic.unwindBy(dy)
                    if (consumed != 0f) return Offset(0f, consumed)
                }

                val atTop = !scrollState.canScrollBackward
                val atBottom = !scrollState.canScrollForward
                if (dy > 0f && atTop) return Offset(0f, elastic.dragBy(dy))
                if (dy < 0f && atBottom) return Offset(0f, elastic.dragBy(dy))
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                val dy = available.y
                if (dy == 0f) return Offset.Zero
                return Offset(0f, elastic.dragBy(dy))
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                return if (elastic.pull != 0f) available else Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                elastic.settle()
                return Velocity.Zero
            }
        }
    }

    this
        .nestedScroll(connection)
        .pointerInput(elastic) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Final)
                    if (event.changes.none { it.pressed }) elastic.settle()
                }
            }
        }
        .graphicsLayer {
            val height = size.height.coerceAtLeast(1f)
            val ratio = (abs(elastic.pull) / height).coerceIn(0f, 1f)
            val stretch = (ratio * 0.75f).coerceAtMost(MAX_STRETCH)
            transformOrigin = TransformOrigin(0.5f, if (elastic.pull >= 0f) 0f else 1f)
            scaleY = 1f + stretch
        }
}
