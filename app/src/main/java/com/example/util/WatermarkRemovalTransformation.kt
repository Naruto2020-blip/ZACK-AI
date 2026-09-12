package com.example.util

import android.graphics.Bitmap
import coil.size.Size
import coil.transform.Transformation

class WatermarkRemovalTransformation(
    private val cropPercentage: Float = 0.048f
) : Transformation {

    override val cacheKey: String = "watermark_removed_${cropPercentage}"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        return cleanBitmap(input, cropPercentage)
    }

    companion object {
        fun cleanBitmap(input: Bitmap, cropPercentage: Float = 0.048f): Bitmap {
            val width = input.width
            val height = input.height
            if (width <= 0 || height <= 0) return input

            // Watermarks and logos are stamped in the bottom 4-5% margin
            val cutHeight = (height * cropPercentage).toInt().coerceIn(32, 64)
            val targetHeight = height - cutHeight
            if (targetHeight <= 0) return input

            return try {
                Bitmap.createBitmap(input, 0, 0, width, targetHeight)
            } catch (e: Exception) {
                input
            }
        }
    }
}
