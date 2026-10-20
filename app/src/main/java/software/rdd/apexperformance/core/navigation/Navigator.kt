package software.rdd.apexperformance.core.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.res.stringResource
import software.rdd.apexperformance.R
import software.rdd.apexperformance.ui.components.ConfirmDialog
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

    // Forms with changes not saved yet: going back asks before they are lost.
    open val hasUnsavedChanges: Boolean get() = false

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

    // Set while asking whether to discard the current screen's unsaved changes.
    var isConfirmingDiscard by mutableStateOf(false)
        private set

    // Back from the user (back button or gesture): asks first when the
    // screen has unsaved changes. Saving and similar actions use pop().
    fun requestPop() {
        if (!canPop) return
        if (current.hasUnsavedChanges) {
            isConfirmingDiscard = true
        } else {
            pop()
        }
    }

    fun discardChanges() {
        isConfirmingDiscard = false
        pop()
    }

    fun keepEditing() {
        isConfirmingDiscard = false
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

    BackHandler(enabled = navigator.canPop) { navigator.requestPop() }

    if (navigator.isConfirmingDiscard) {
        ConfirmDialog(
            title = stringResource(R.string.discard_changes_question),
            message = stringResource(R.string.discard_changes_info),
            confirmText = stringResource(R.string.discard_changes),
            dismissText = stringResource(R.string.keep_editing),
            onConfirm = { navigator.discardChanges() },
            onDismiss = { navigator.keepEditing() }
        )
    }

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
