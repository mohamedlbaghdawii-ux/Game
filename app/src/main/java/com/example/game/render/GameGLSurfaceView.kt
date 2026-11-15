package com.example.game.render

import android.opengl.GLSurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.game.engine.GameEngine
import com.example.game.model.WeaponType

/**
 * Composable wrapper for the 60 FPS OpenGL ES 3D game canvas with lifecycle pause/resume handling
 * and Armory 3D weapon preview synchronization.
 */
@Composable
fun GameGLSurfaceView(
    engine: GameEngine,
    armoryPreviewWeapon: WeaponType?,
    onViewportChanged: (Int, Int) -> Unit,
    onFrameStepped: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val renderer = remember {
        ArenaRenderer(engine, onViewportChanged, onFrameStepped)
    }

    SideEffect {
        renderer.armoryPreviewWeapon = armoryPreviewWeapon
    }

    val glSurfaceView = remember {
        GLSurfaceView(context).apply {
            setEGLContextClientVersion(2)
            setEGLConfigChooser(8, 8, 8, 8, 16, 0)
            preserveEGLContextOnPause = true
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }
    }

    DisposableEffect(lifecycleOwner, glSurfaceView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> glSurfaceView.onResume()
                Lifecycle.Event.ON_PAUSE -> glSurfaceView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            glSurfaceView.onPause()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { glSurfaceView }
    )
}
