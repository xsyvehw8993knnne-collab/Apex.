package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PointF
import android.graphics.RectF
import androidx.test.core.app.ApplicationProvider
import com.example.data.SettingsDataStore
import com.example.domain.model.GameProfile
import com.example.service.DetectedScreenTarget
import com.example.service.TargetKind
import com.example.service.VisionTargetTracker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VisionTrackerTest {

    private fun fillRectPixels(bitmap: Bitmap, left: Int, top: Int, right: Int, bottom: Int, color: Int) {
        val w = right - left
        val h = bottom - top
        val row = IntArray(w) { color }
        for (y in top until bottom) {
            bitmap.setPixels(row, 0, w, left, y, w, 1)
        }
    }

    @Test
    fun `motion cluster detector locates moving sprite in landscape frame`() {
        val tracker = VisionTargetTracker()
        val w = 320
        val h = 180
        val screenW = 1920
        val screenH = 1080

        val frame1 = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(30, 30, 35))
        }
        // Baseline frame establishes luma grid
        assertTrue(tracker.detectMotionClusters(frame1, screenW, screenH).isEmpty())

        // Frame 2: moving target appears around (180..230, 50..130)
        val frame2 = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(30, 30, 35))
            fillRectPixels(this, 180, 50, 230, 130, Color.rgb(220, 220, 220))
        }

        val detections = tracker.detectMotionClusters(frame2, screenW, screenH)
        assertTrue("Expected at least one moving target", detections.isNotEmpty())
        val box = detections.first().bounds
        // Scaled center should fall near x = 205 * 6 = 1230, y = 90 * 6 = 540
        assertTrue("Expected target center X near 1230, got ${box.centerX()}", box.centerX() in 950f..1450f)
        assertTrue("Expected target center Y near 540, got ${box.centerY()}", box.centerY() in 300f..780f)
    }

    @Test
    fun `motion detector suppresses uniform camera pan`() {
        val tracker = VisionTargetTracker()
        val frame1 = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(40, 40, 40))
        }
        tracker.detectMotionClusters(frame1, 1920, 1080)

        // Entire screen shifts luminance uniformly by +45 (camera whip / flash)
        val frame2 = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(85, 85, 85))
        }
        val detections = tracker.detectMotionClusters(frame2, 1920, 1080)
        assertTrue("Uniform pan should be suppressed", detections.isEmpty())
    }

    @Test
    fun `motion detector excludes floating HUD region`() {
        val tracker = VisionTargetTracker()
        val frame1 = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(25, 25, 25))
        }
        tracker.detectMotionClusters(frame1, 1920, 1080)

        // Bright change strictly inside top-left HUD exclusion zone (plus a real sprite outside)
        val frame2 = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(25, 25, 25))
            fillRectPixels(this, 10, 15, 55, 55, Color.WHITE)
            fillRectPixels(this, 200, 70, 240, 120, Color.WHITE)
        }
        val hudRect = RectF(0f, 0f, 400f, 400f)
        val detections = tracker.detectMotionClusters(frame2, 1920, 1080, excludedHudRect = hudRect)
        assertEquals("Only non-HUD target should be detected", 1, detections.size)
        assertTrue(detections.first().bounds.centerX() > 900f)
    }

    @Test
    fun `fusion merges overlapping detections and preserves humanoid head landmark`() {
        val tracker = VisionTargetTracker()
        val bodyRect = RectF(500f, 200f, 660f, 600f)
        val overlapMotion = RectF(510f, 220f, 650f, 580f)
        val exactHead = PointF(580f, 240f)

        val fused = tracker.fuseAndSmooth(
            listOf(
                DetectedScreenTarget(overlapMotion, PointF(580f, 280f), TargetKind.MOTION_OBJECT, 0.7f),
                DetectedScreenTarget(bodyRect, exactHead, TargetKind.HUMANOID, 0.96f)
            )
        )

        assertEquals(1, fused.size)
        assertEquals(TargetKind.HUMANOID, fused.first().kind)
        assertNotNull(fused.first().headCenter)
        assertEquals(240f, fused.first().headCenter!!.y, 0.5f)

        // Brief 1-frame drop persists smoothly without flickering
        val persisted = tracker.fuseAndSmooth(emptyList())
        assertEquals(1, persisted.size)
    }

    @Test
    fun `head lock guidance prioritizes humanoid and detects alignment within radius`() {
        val crosshairX = 960f
        val crosshairY = 540f

        val farMotion = DetectedScreenTarget(
            bounds = RectF(900f, 500f, 950f, 580f),
            headCenter = PointF(925f, 515f),
            kind = TargetKind.MOTION_OBJECT,
            confidence = 0.7f
        )
        val humanoidTarget = DetectedScreenTarget(
            bounds = RectF(920f, 480f, 1020f, 760f),
            headCenter = PointF(970f, 548f),
            kind = TargetKind.HUMANOID,
            confidence = 0.96f
        )

        val lockedGuidance = VisionTargetTracker.computeHeadLockGuidance(
            targets = listOf(farMotion, humanoidTarget),
            crosshairX = crosshairX,
            crosshairY = crosshairY,
            lockRadiusPx = 42f
        )

        assertNotNull(lockedGuidance)
        assertTrue(lockedGuidance!!.isLockedOnHead)
        assertEquals(970f, lockedGuidance.headPoint.x, 0.1f)

        val offTargetGuidance = VisionTargetTracker.computeHeadLockGuidance(
            targets = listOf(humanoidTarget.copy(headCenter = PointF(1120f, 420f))),
            crosshairX = crosshairX,
            crosshairY = crosshairY,
            lockRadiusPx = 42f
        )
        assertNotNull(offTargetGuidance)
        assertFalse(offTargetGuidance!!.isLockedOnHead)
    }

    @Test
    fun `datastore persists locked headshot anchor coordinates per game`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = SettingsDataStore(context)
        val pkg = "com.rockstargames.gtasa"

        store.addOrUpdateGame(GameProfile(packageName = pkg, displayName = "GTA San Andreas"))
        store.updateHeadshotAnchor(pkg, 0.84f, 0.79f, locked = true)

        val state = store.stateFlow.first()
        val saved = state.savedGames.firstOrNull { it.packageName == pkg }
        assertNotNull(saved)
        assertTrue(saved!!.headshotAnchorLocked)
        assertEquals(0.84f, saved.headshotAnchorX!!, 0.001f)
        assertEquals(0.79f, saved.headshotAnchorY!!, 0.001f)

        store.updateHeadshotAnchor(pkg, null, null, locked = false)
        val reset = store.stateFlow.first().savedGames.first { it.packageName == pkg }
        assertFalse(reset.headshotAnchorLocked)
    }
}
