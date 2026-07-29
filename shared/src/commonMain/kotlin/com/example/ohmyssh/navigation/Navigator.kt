package com.example.ohmyssh.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import com.example.ohmyssh.theme.appColors
import kotlinx.coroutines.CompletableDeferred

class NavEntry internal constructor(
    internal val id: Long,
    internal val content: @Composable () -> Unit,
) {
    internal val result = CompletableDeferred<Any?>()
}

class Navigator {
    internal val entries = mutableStateListOf<NavEntry>()
    private var nextId = 0L

    val canPop: Boolean get() = entries.isNotEmpty()

    fun push(content: @Composable () -> Unit) {
        entries.add(NavEntry(nextId++, content))
    }

    suspend fun <T> pushForResult(content: @Composable () -> Unit): T? {
        val entry = NavEntry(nextId++, content)
        entries.add(entry)
        @Suppress("UNCHECKED_CAST")
        return entry.result.await() as? T
    }

    fun pop(result: Any? = null) {
        val entry = entries.removeLastOrNull() ?: return
        entry.result.complete(result)
    }
}

val LocalNavigator = staticCompositionLocalOf<Navigator> {
    error("No Navigator in scope")
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun NavigationHost(
    navigator: Navigator,
    root: @Composable () -> Unit,
) {
    val holder: SaveableStateHolder = rememberSaveableStateHolder()
    val lastCount = remember { intArrayOf(0) }

    CompositionLocalProvider(LocalNavigator provides navigator) {
        PlatformBackHandler(enabled = navigator.canPop) { navigator.pop() }

        val top = navigator.entries.lastOrNull()
        AnimatedContent(
            targetState = top,
            transitionSpec = {
                val count = navigator.entries.size
                val pushing = count >= lastCount[0]
                lastCount[0] = count
                (fadeIn(tween(200)) + scaleIn(tween(260), if (pushing) 0.985f else 1.015f))
                    .togetherWith(
                        fadeOut(tween(160)) +
                            scaleOut(tween(260), if (pushing) 1.015f else 0.985f),
                    )
            },
            contentKey = { it?.id ?: -1L },
        ) { entry ->
            Box(Modifier.fillMaxSize().background(appColors.background)) {
                if (entry == null) {
                    holder.SaveableStateProvider(-1L) { root() }
                } else {
                    holder.SaveableStateProvider(entry.id) { entry.content() }
                }
            }
        }
    }
}

@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)
