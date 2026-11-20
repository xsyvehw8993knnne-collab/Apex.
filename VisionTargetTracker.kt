package com.example.service

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

enum class TargetKind {
    HUMANOID,
    MOTION_OBJECT
}

data class DetectedScreenTarget(
    val bounds: RectF,
    val headCenter: PointF? = null,
    val kind: TargetKind = TargetKind.MOTION_OBJECT,
    val confidence: Float = 1f,
    val trackId: Int = 0
)

data class HeadLockGuidance(
    val headPoint: PointF,
    val crosshairPoint: PointF,
    val distancePx: Float,
    val isLockedOnHead: Boolean,
    val confidence: Float
)

/**
 * Pure, testable computer-vision geometry & tracking engine:
 * 1. High-speed flat pixel-array motion cluster detector (with global camera-pan median compensation
 *    and optional exclusion rectangles for our own HUD/orb).
 * 2. IoU-based duplicate bounding-box fusion (merges Pose + Object + Motion detections on the same target).
 * 3. Temporal EMA smoothing & brief drop-frame persistence so tracking boxes & Head Lock reticles
 *    never flicker on fast action frames.
 * 4. Aim-alignment guidance calculator from screen crosshair to closest humanoid Head Landmark.
 */
class VisionTargetTracker {

    private var previousLumaGrid: IntArray? = null
    private var nextTrackId = 1
    private val activeTracks = ArrayList<TrackedEntry>()

    private data class TrackedEntry(
        val id: Int,
        var bounds: RectF,
        var headCenter: PointF?,
        var kind: TargetKind,
        var confidence: Float,
        var missedFrames: Int
    )

    fun reset() {
        previousLumaGrid = null
        activeTracks.clear()
    }

    /**
     * Analyzes consecutive captured bitmaps using a 32x18 spatial luminance grid.
     * Uses single-call `getPixels()` for speed and subtracts the frame's median delta
     * to reject uniform camera panning.
     */
    fun detectMotionClusters(
        bitmap: Bitmap,
        screenWidth: Int,
        screenHeight: Int,
        excludedHudRect: RectF? = null
    ): List<DetectedScreenTarget> {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 32 || h < 18) return emptyList()

        val cols = 32
        val rows = 18
        val cellW = (w / cols).coerceAtLeast(1)
        val cellH = (h / rows).coerceAtLeast(1)

        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val grid = IntArray(cols * rows)
        for (r in 0 until rows) {
            val baseY = (r * cellH + cellH / 2).coerceIn(0, h - 2)
            for (c in 0 until cols) {
                val baseX = (c * cellW + cellW / 2).coerceIn(0, w - 2)
                // 2x2 neighborhood average reduces single-pixel shader/compression noise
                var sumLum = 0
                for (dy in 0..1) {
                    val rowOffset = (baseY + dy) * w
                    for (dx in 0..1) {
                        val px = pixels[rowOffset + baseX + dx]
                        sumLum += (((px shr 16) and 0xFF) * 3 +
                            ((px shr 8) and 0xFF) * 6 +
                            (px and 0xFF)) / 10
                    }
                }
                grid[r * cols + c] = sumLum / 4
            }
        }

        val prev = previousLumaGrid
        previousLumaGrid = grid
        if (prev == null || prev.size != grid.size) return emptyList()

        val deltas = IntArray(grid.size)
        for (i in grid.indices) {
            deltas[i] = abs(grid[i] - prev[i])
        }

        val sorted = deltas.copyOf().apply { sort() }
        val medianDelta = sorted[sorted.size / 2]
        // Suppress global full-screen camera whips
        if (medianDelta > 26) return emptyList()

        val threshold = max(20, medianDelta + 16)
        val scaleX = screenWidth.toFloat() / w.toFloat()
        val scaleY = screenHeight.toFloat() / h.toFloat()

        val active = BooleanArray(grid.size)
        for (i in deltas.indices) {
            val r = i / cols
            val c = i % cols
            if (r in 1 until (rows - 1) && deltas[i] > threshold) {
                val cx = (c + 0.5f) * cellW * scaleX
                val cy = (r + 0.5f) * cellH * scaleY
                if (excludedHudRect == null || !excludedHudRect.contains(cx, cy)) {
                    active[i] = true
                }
            }
        }

        val visited = BooleanArray(grid.size)
        val boxes = ArrayList<DetectedScreenTarget>()

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val idx = r * cols + c
                if (!active[idx] || visited[idx]) continue

                var minR = r
                var maxR = r
                var minC = c
                var maxC = c
                var count = 0
                val queue = ArrayDeque<Int>()
                queue.add(idx)
                visited[idx] = true

                while (queue.isNotEmpty()) {
                    val cur = queue.removeFirst()
                    val cr = cur / cols
                    val cc = cur % cols
                    count++
                    if (cr < minR) minR = cr
                    if (cr > maxR) maxR = cr
                    if (cc < minC) minC = cc
                    if (cc > maxC) maxC = cc

                    for (dr in -1..1) {
                        for (dc in -1..1) {
                            val nr = cr + dr
                            val nc = cc + dc
                            if (nr in 0 until rows && nc in 0 until cols) {
                                val nIdx = nr * cols + nc
                                if (active[nIdx] && !visited[nIdx]) {
                                    visited[nIdx] = true
                                    queue.add(nIdx)
                                }
                            }
                        }
                    }
                }

                // Filter out tiny 1-cell noise and huge background shifts (>30% screen)
                if (count in 2..64) {
                    val rect = RectF(
                        (minC * cellW * scaleX).coerceAtLeast(0f),
                        (minR * cellH * scaleY).coerceAtLeast(0f),
                        ((maxC + 1) * cellW * scaleX).coerceAtMost(screenWidth.toFloat()),
                        ((maxR + 1) * cellH * scaleY).coerceAtMost(screenHeight.toFloat())
                    )
                    boxes.add(
                        DetectedScreenTarget(
                            bounds = rect,
                            headCenter = PointF(rect.centerX(), rect.top + rect.height() * 0.17f),
                            kind = TargetKind.MOTION_OBJECT,
                            confidence = (0.55f + min(0.35f, count * 0.02f))
                        )
                    )
                }
            }
        }

        return boxes.sortedByDescending { it.bounds.width() * it.bounds.height() }.take(6)
    }

    /**
     * Merges overlapping detections from Pose, Object, and Motion detectors, preferring HUMANOID
     * bounds & real head landmarks over generic motion rectangles, then smooths tracks temporally.
     */
    fun fuseAndSmooth(rawDetections: List<DetectedScreenTarget>): List<DetectedScreenTarget> {
        val sorted = rawDetections.sortedWith(
            compareByDescending<DetectedScreenTarget> { it.kind == TargetKind.HUMANOID }
                .thenByDescending { it.confidence }
        )

        val merged = ArrayList<DetectedScreenTarget>()
        for (candidate in sorted) {
            val existingIdx = merged.indexOfFirst { computeIoU(it.bounds, candidate.bounds) >= 0.35f }
            if (existingIdx < 0) {
                merged.add(candidate)
            } else {
                val existing = merged[existingIdx]
                // Keep humanoid classification and head landmark if either has it
                if (existing.kind != TargetKind.HUMANOID && candidate.kind == TargetKind.HUMANOID) {
                    merged[existingIdx] = candidate
                } else if (existing.headCenter == null && candidate.headCenter != null) {
                    merged[existingIdx] = existing.copy(headCenter = candidate.headCenter)
                }
            }
        }

        // Temporal matching & EMA smoothing across consecutive frames
        val matchedTrackIds = HashSet<Int>()
        val alpha = 0.55f // Responsiveness vs jitter damping

        for (det in merged) {
            val match = activeTracks
                .filter { it.id !in matchedTrackIds }
                .maxByOrNull { track -> computeIoU(track.bounds, det.bounds) }
                ?.takeIf { computeIoU(it.bounds, det.bounds) >= 0.22f }

            if (match != null) {
                matchedTrackIds.add(match.id)
                match.bounds = RectF(
                    lerp(match.bounds.left, det.bounds.left, alpha),
                    lerp(match.bounds.top, det.bounds.top, alpha),
                    lerp(match.bounds.right, det.bounds.right, alpha),
                    lerp(match.bounds.bottom, det.bounds.bottom, alpha)
                )
                match.headCenter = when {
                    match.headCenter != null && det.headCenter != null -> PointF(
                        lerp(match.headCenter!!.x, det.headCenter.x, 0.65f),
                        lerp(match.headCenter!!.y, det.headCenter.y, 0.65f)
                    )
                    det.headCenter != null -> det.headCenter
                    else -> match.headCenter
                }
                if (det.kind == TargetKind.HUMANOID) {
                    match.kind = TargetKind.HUMANOID
                }
                match.confidence = max(match.confidence * 0.5f + det.confidence * 0.5f, det.confidence)
                match.missedFrames = 0
            } else {
                val id = nextTrackId++
                matchedTrackIds.add(id)
                activeTracks.add(
                    TrackedEntry(
                        id = id,
                        bounds = RectF(det.bounds),
                        headCenter = det.headCenter?.let { PointF(it.x, it.y) },
                        kind = det.kind,
                        confidence = det.confidence,
                        missedFrames = 0
                    )
                )
            }
        }

        // Brief 2-frame persistence prevents annoying flicker if 1 frame blurs during action
        val iterator = activeTracks.iterator()
        while (iterator.hasNext()) {
            val track = iterator.next()
            if (track.id !in matchedTrackIds) {
                track.missedFrames++
                if (track.missedFrames > 2) {
                    iterator.remove()
                }
            }
        }

        return activeTracks.map {
            DetectedScreenTarget(
                bounds = RectF(it.bounds),
                headCenter = it.headCenter?.let { pt -> PointF(pt.x, pt.y) },
                kind = it.kind,
                confidence = it.confidence,
                trackId = it.id
            )
        }
    }

    companion object {
        fun computeIoU(a: RectF, b: RectF): Float {
            val left = max(a.left, b.left)
            val top = max(a.top, b.top)
            val right = min(a.right, b.right)
            val bottom = min(a.bottom, b.bottom)
            if (right <= left || bottom <= top) return 0f
            val intersection = (right - left) * (bottom - top)
            val areaA = a.width() * a.height()
            val areaB = b.width() * b.height()
            val union = areaA + areaB - intersection
            return if (union > 0f) intersection / union else 0f
        }

        fun computeHeadLockGuidance(
            targets: List<DetectedScreenTarget>,
            crosshairX: Float,
            crosshairY: Float,
            lockRadiusPx: Float = 42f
        ): HeadLockGuidance? {
            if (targets.isEmpty()) return null
            // Prioritize real HUMANOID targets near the aiming crosshair
            val selected = targets
                .filter { it.headCenter != null }
                .minByOrNull { target ->
                    val head = target.headCenter!!
                    val dist = hypot(head.x - crosshairX, head.y - crosshairY)
                    // Give humanoid targets a strong priority bonus over generic motion objects
                    if (target.kind == TargetKind.HUMANOID) dist else dist + 280f
                } ?: return null

            val head = selected.headCenter ?: return null
            val dist = hypot(head.x - crosshairX, head.y - crosshairY)
            return HeadLockGuidance(
                headPoint = head,
                crosshairPoint = PointF(crosshairX, crosshairY),
                distancePx = dist,
                isLockedOnHead = dist <= lockRadiusPx,
                confidence = selected.confidence
            )
        }

        private fun lerp(start: Float, end: Float, factor: Float): Float =
            start + (end - start) * factor
    }
}
