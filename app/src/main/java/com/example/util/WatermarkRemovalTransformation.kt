package com.example.util

import android.graphics.Bitmap
import coil.size.Size
import coil.transform.Transformation

class WatermarkRemovalTransformation(
    private val imageUrl: String? = null,
    private val cropPercentage: Float = 0.048f
) : Transformation {

    override val cacheKey: String = "watermark_removed_${imageUrl}_${cropPercentage}"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        // If image is a real internet photo/emblem (e.g. Wikipedia/Wikimedia), do not crop
        if (imageUrl != null && !imageUrl.contains("pollinations.ai", ignoreCase = true)) {
            return input
        }
        return cleanBitmap(input, cropPercentage)
    }

    companion object {
        fun cleanBitmap(input: Bitmap, cropPercentage: Float = 0.048f): Bitmap {
            val width = input.width
            val height = input.height
            if (width <= 0 || height <= 0) return input

            // Watermarks and logos are stamped in the bottom 4-5% margin of generative AI models
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

