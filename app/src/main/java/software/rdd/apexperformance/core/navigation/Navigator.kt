package software.rdd.apexperformance.core.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import java.util.UUID

// A screen keeps its own state while it is on the stack, like a SwiftUI view in
// a NavigationStack, so going back shows it as it was left. Work started from
// it (saving, deleting...) runs in `scope`, which ends when it is popped.
abstract class Screen {
    val key: String = UUID.randomUUID().toString()
    val scope = MainScope()

    @Composable
    abstract fun Content()

    open fun onDispose() {
        scope.cancel()
    }
}

// Stack of screens of one tab (or of the login flow).
class Navigator(root: Screen) {
    val stack = mutableStateListOf(root)

    // Set by push/pop so the transition slides the right way.
    internal var isPushing = true
        private set

    val current: Screen get() = stack.last()
    val canPop: Boolean get() = stack.size > 1

    fun push(screen: Screen) {
        isPushing = true
        stack.add(screen)
    }

    fun pop(): Boolean {
        if (!canPop) return false
        isPushing = false
        stack.removeAt(stack.lastIndex).onDispose()
        return true
    }

    fun popToRoot() {
        isPushing = false
        while (canPop) stack.removeAt(stack.lastIndex).onDispose()
    }

    fun disposeAll() {
        stack.forEach { it.onDispose() }
    }
}

val LocalNavigator = staticCompositionLocalOf<Navigator> { error("No navigator") }

@Composable
fun NavigatorHost(navigator: Navigator) {
    val stateHolder = rememberSaveableStateHolder()

    BackHandler(enabled = navigator.canPop) { navigator.pop() }

    CompositionLocalProvider(LocalNavigator provides navigator) {
        AnimatedContent(
            targetState = navigator.current,
            transitionSpec = {
                if (navigator.isPushing) {
                    slideInHorizontally { it } togetherWith slideOutHorizontally { -it / 3 }
                } else {
                    slideInHorizontally { -it / 3 } togetherWith slideOutHorizontally { it }
                }
            },
            contentKey = { it.key },
            label = "navigation"
        ) { screen ->
            stateHolder.SaveableStateProvider(screen.key) {
                screen.Content()
            }
        }
    }
}
