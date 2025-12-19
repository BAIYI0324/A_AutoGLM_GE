package com.sidhu.androidautoglm

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.OpenInNew
import android.content.Intent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt

class FloatingWindowController(private val context: Context) : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var floatView: ComposeView? = null
    private var isShowing = false
    private lateinit var windowParams: WindowManager.LayoutParams
    
    // State for the UI
    private var _statusText by mutableStateOf("")
    private var _isTaskRunning by mutableStateOf(true)
    private var _onStopClick: (() -> Unit)? = null

    // Lifecycle components required for Compose
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val viewModelStore = ViewModelStore()

    init {
        _statusText = context.getString(R.string.fw_ready)
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    fun show(onStop: () -> Unit) {
        if (isShowing) return
        _onStopClick = onStop
        _isTaskRunning = true
        
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        val metrics = context.resources.displayMetrics
        val screenHeight = metrics.heightPixels

        windowParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )

        windowParams.gravity = Gravity.BOTTOM or Gravity.START
        windowParams.x = 0
        windowParams.y = 20 // Initial position near bottom

        floatView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(this@FloatingWindowController)
            setViewTreeViewModelStoreOwner(this@FloatingWindowController)
            setViewTreeSavedStateRegistryOwner(this@FloatingWindowController)
            
            setContent {
                FloatingWindowContent(
                    status = _statusText,
                    isTaskRunning = _isTaskRunning,
                    onAction = {
                        if (_isTaskRunning) {
                            _onStopClick?.invoke()
                            // Do not hide immediately, wait for task to finish or user to click close
                        } else {
                            // Launch App
                            try {
                                val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                                if (intent != null) {
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(intent)
                                    hide()
                                } else {
                                    Log.e("FloatingWindow", "Launch intent not found")
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    },
                    onDrag = { x, y ->
                        windowParams.x += x.roundToInt()
                        windowParams.y -= y.roundToInt()
                        try {
                            windowManager.updateViewLayout(floatView, windowParams)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                )
            }
        }

        try {
            windowManager.addView(floatView, windowParams)
            isShowing = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun updateStatus(status: String) {
        _statusText = status
    }

    fun setTaskRunning(running: Boolean) {
        _isTaskRunning = running
    }

    fun setScreenshotMode(isScreenshotting: Boolean) {
        if (!isShowing || floatView == null) return
        
        try {
            floatView?.visibility = if (isScreenshotting) android.view.View.GONE else android.view.View.VISIBLE
            
            // Update flags to ensure touches pass through when hidden
            if (isScreenshotting) {
                windowParams.flags = windowParams.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                windowParams.width = 0
                windowParams.height = 0
            } else {
                windowParams.flags = windowParams.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                windowParams.width = WindowManager.LayoutParams.WRAP_CONTENT
                windowParams.height = WindowManager.LayoutParams.WRAP_CONTENT
            }
            windowManager.updateViewLayout(floatView, windowParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun isOccupyingSpace(x: Float, y: Float): Boolean {
        if (!isShowing || floatView == null || floatView?.visibility != android.view.View.VISIBLE) return false
        
        val location = IntArray(2)
        floatView?.getLocationOnScreen(location)
        val viewX = location[0]
        val viewY = location[1]
        val width = floatView?.width ?: 0
        val height = floatView?.height ?: 0
        
        return x >= viewX && x <= (viewX + width) && y >= viewY && y <= (viewY + height)
    }

    fun avoidArea(targetX: Float, targetY: Float) {
        if (!isOccupyingSpace(targetX, targetY)) return
        
        val metrics = context.resources.displayMetrics
        val screenHeight = metrics.heightPixels
        
        // If target is in bottom half, move window to top. Else move to bottom.
        val targetInBottomHalf = targetY > screenHeight / 2
        
        val newY = if (targetInBottomHalf) {
            screenHeight - 300 // Top (distance from bottom)
        } else {
            20 // Bottom (distance from bottom)
        }
        
        // Only update if significantly different
        if (kotlin.math.abs(windowParams.y - newY) > 200) {
            windowParams.y = newY
            try {
                windowManager.updateViewLayout(floatView, windowParams)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun hide() {
        if (!isShowing) return
        
        Log.d("FloatingWindow", "hide() called", Exception("Stack trace"))

        try {
            windowManager.removeView(floatView)
            isShowing = false
            floatView = null
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry
}

@Composable
fun FloatingWindowContent(
    status: String,
    isTaskRunning: Boolean,
    onAction: () -> Unit,
    onDrag: (Float, Float) -> Unit
) {
    MaterialTheme {
        Surface(
            modifier = Modifier
                .width(350.dp) // Fixed width for consistent dragging
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.x, dragAmount.y)
                    }
                }
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    val isError = status.startsWith("Error") || status.startsWith("出错") || status.startsWith("运行异常")
                    val titleText = when {
                        isTaskRunning -> stringResource(R.string.fw_running)
                        isError -> stringResource(R.string.fw_error_title)
                        else -> stringResource(R.string.fw_ready_title)
                    }
                    val titleColor = when {
                        isTaskRunning -> Color.Gray
                        isError -> MaterialTheme.colorScheme.error
                        else -> Color(0xFF4CAF50)
                    }

                    Text(
                        text = titleText,
                        style = MaterialTheme.typography.labelSmall,
                        color = titleColor
                    )
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1
                    )
                }
                
                Button(
                    onClick = onAction,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isTaskRunning) Color(0xFFFFEBEE) else Color(0xFFE3F2FD),
                        contentColor = if (isTaskRunning) Color.Red else Color(0xFF2196F3)
                    ),
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(
                        if (isTaskRunning) Icons.Default.Stop else Icons.Default.OpenInNew, 
                        contentDescription = null, 
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isTaskRunning) stringResource(R.string.fw_stop) else stringResource(R.string.fw_return_app))
                }
            }
        }
    }
}
