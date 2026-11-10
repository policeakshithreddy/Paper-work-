package com.example.core.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.example.domain.model.ScanFilterMode
import java.io.File

/**
 * Normalized 4-corner quad in [0f..1f] coordinates for perspective correction and deskew.
 */
data class NormalizedQuadCorners(
    val topLeft: PointF = PointF(0.0f, 0.0f),
    val topRight: PointF = PointF(1.0f, 0.0f),
    val bottomRight: PointF = PointF(1.0f, 1.0f),
    val bottomLeft: PointF = PointF(0.0f, 1.0f)
) {
    fun isIdentity(): Boolean {
        val eps = 0.01f
        return kotlin.math.abs(topLeft.x - 0f) < eps &&
            kotlin.math.abs(topLeft.y - 0f) < eps &&
            kotlin.math.abs(topRight.x - 1f) < eps &&
            kotlin.math.abs(topRight.y - 0f) < eps &&
            kotlin.math.abs(bottomRight.x - 1f) < eps &&
            kotlin.math.abs(bottomRight.y - 1f) < eps &&
            kotlin.math.abs(bottomLeft.x - 0f) < eps &&
            kotlin.math.abs(bottomLeft.y - 1f) < eps
    }
}

/**
 * OOM-safe image decoding, EXIF orientation normalization, perspective correction (deskew),
 * and color-preserving vs. optional grayscale document enhancement.
 */
object SafeBitmapDecoder {

    /**
     * Decodes a [Uri] safely using two-pass bounds inspection (`inJustDecodeBounds = true`),
     * power-of-two `inSampleSize` downsampling, and `ExifInterface` orientation normalization
     * so 48MP+ camera photos and mixed-dimension images never trigger `OutOfMemoryError`.
     */
    fun decodeUriSafely(
        context: Context,
        uri: Uri,
        maxDimensionPx: Int = 2400
    ): Bitmap? {
        return try {
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            }
            if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) {
                return null
            }

            val sampleSize = calculateInSampleSize(
                rawWidth = boundsOptions.outWidth,
                rawHeight = boundsOptions.outHeight,
                maxDimensionPx = maxDimensionPx
            )

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            val rawBitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return null

            val exifRotationAndFlip = context.contentResolver.openInputStream(uri)?.use { stream ->
                readExifMatrix(ExifInterface(stream))
            } ?: Matrix()

            val oriented = if (!exifRotationAndFlip.isIdentity) {
                Bitmap.createBitmap(
                    rawBitmap,
                    0,
                    0,
                    rawBitmap.width,
                    rawBitmap.height,
                    exifRotationAndFlip,
                    true
                )
            } else {
                rawBitmap
            }

            scaleDownToMaxDimension(oriented, maxDimensionPx)
        } catch (_: OutOfMemoryError) {
            System.gc()
            null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Decodes a local [File] safely with two-pass `inSampleSize` and `ExifInterface` orientation.
     */
    fun decodeFileSafely(
        file: File,
        maxDimensionPx: Int = 2400
    ): Bitmap? {
        if (!file.exists()) return null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDimensionPx)
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val raw = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return null
            val exifMatrix = runCatching { readExifMatrix(ExifInterface(file)) }.getOrDefault(Matrix())
            val oriented = if (!exifMatrix.isIdentity) {
                Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, exifMatrix, true)
            } else {
                raw
            }
            scaleDownToMaxDimension(oriented, maxDimensionPx)
        } catch (_: OutOfMemoryError) {
            System.gc()
            null
        } catch (_: Exception) {
            null
        }
    }

    fun calculateInSampleSize(rawWidth: Int, rawHeight: Int, maxDimensionPx: Int): Int {
        var inSampleSize = 1
        val maxRaw = maxOf(rawWidth, rawHeight)
        if (maxRaw > maxDimensionPx) {
            val halfW = rawWidth / 2
            val halfH = rawHeight / 2
            while ((halfW / inSampleSize) >= maxDimensionPx || (halfH / inSampleSize) >= maxDimensionPx) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }

    fun scaleDownToMaxDimension(bitmap: Bitmap, maxDimensionPx: Int): Bitmap {
        val maxSide = maxOf(bitmap.width, bitmap.height)
        if (maxSide <= maxDimensionPx) return bitmap
        val scale = maxDimensionPx.toFloat() / maxSide.toFloat()
        val targetW = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val targetH = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
    }

    private fun readExifMatrix(exif: ExifInterface): Matrix {
        val matrix = Matrix()
        when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.preScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.preScale(-1f, 1f)
            }
        }
        return matrix
    }

    /**
     * Applies 4-corner perspective correction and deskew using `Matrix.setPolyToPoly`
     * mapping the selected quadrilateral corners onto a rectangular document page.
     */
    fun applyPerspectiveAndDeskew(
        source: Bitmap,
        quad: NormalizedQuadCorners,
        deskewAngleDegrees: Float = 0f
    ): Bitmap {
        if (quad.isIdentity() && kotlin.math.abs(deskewAngleDegrees) < 0.05f) {
            return source
        }

        val w = source.width.toFloat()
        val h = source.height.toFloat()

        val srcPts = floatArrayOf(
            quad.topLeft.x.coerceIn(0f, 0.95f) * w,
            quad.topLeft.y.coerceIn(0f, 0.95f) * h,
            quad.topRight.x.coerceIn(0.05f, 1f) * w,
            quad.topRight.y.coerceIn(0f, 0.95f) * h,
            quad.bottomRight.x.coerceIn(0.05f, 1f) * w,
            quad.bottomRight.y.coerceIn(0.05f, 1f) * h,
            quad.bottomLeft.x.coerceIn(0f, 0.95f) * w,
            quad.bottomLeft.y.coerceIn(0.05f, 1f) * h
        )

        val dstPts = floatArrayOf(
            0f, 0f,
            w, 0f,
            w, h,
            0f, h
        )

        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.WHITE)

        val polyMatrix = Matrix()
        // Map source quad corners to rectangular destination by inverting dst->src mapping
        val mapped = polyMatrix.setPolyToPoly(srcPts, 0, dstPts, 0, 4)
        if (!mapped) {
            return source
        }
        if (kotlin.math.abs(deskewAngleDegrees) >= 0.05f) {
            polyMatrix.postRotate(deskewAngleDegrees, w / 2f, h / 2f)
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(source, polyMatrix, paint)
        return output
    }

    /**
     * Applies one of Paperwork's 3 non-destructive scan filter modes:
     * - [ScanFilterMode.ORIGINAL]: Untouched camera capture.
     * - [ScanFilterMode.ENHANCED_COLOR]: Default color-preserving contrast/shadow cleanup
     *   (saturation 0.92f so blue/red stamps, signatures, and colored highlights remain vivid).
     * - [ScanFilterMode.INK_SAVING_GRAYSCALE]: Optional monochrome high-contrast mode (saturation 0f).
     */
    fun applyScanFilter(source: Bitmap, filterMode: ScanFilterMode): Bitmap {
        if (filterMode == ScanFilterMode.ORIGINAL) {
            return source.copy(Bitmap.Config.ARGB_8888, false)
        }

        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.WHITE)

        val contrast = if (filterMode == ScanFilterMode.ENHANCED_COLOR) 1.36f else 1.52f
        val brightnessLift = if (filterMode == ScanFilterMode.ENHANCED_COLOR) 14f else 18f
        val translate = (-0.5f * contrast + 0.5f) * 255f + brightnessLift

        val contrastMatrix = ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, translate,
                0f, contrast, 0f, 0f, translate,
                0f, 0f, contrast, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )

        val saturation = if (filterMode == ScanFilterMode.ENHANCED_COLOR) 0.92f else 0.0f
        val combinedMatrix = ColorMatrix().apply {
            setSaturation(saturation)
            postConcat(contrastMatrix)
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(combinedMatrix)
        }
        canvas.drawBitmap(source, 0f, 0f, paint)
        return output
    }
}
