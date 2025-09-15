package com.quantumqr.util


import com.quantumqr.R

import android.content.Context
import android.widget.Toast
import androidx.camera.core.Camera
import androidx.camera.core.TorchState
import com.google.android.material.button.MaterialButton

object TorchUtils {
    fun attachTorch(camera: Camera, context: Context, btn: MaterialButton) {
        // initial label
        val info = camera.cameraInfo
        fun updateLabel(state: Int) {
            btn.text = if (state == TorchState.ON) "Torch On" else "Torch Off"
        }
        updateLabel(info.torchState.value ?: TorchState.OFF)
        info.torchState.observeForever { s -> updateLabel(s ?: TorchState.OFF) }

        btn.setOnClickListener {
            val nowOn = info.torchState.value == TorchState.ON
            camera.cameraControl.enableTorch(!nowOn)
        }
    }
}