package com.vayunmathur.photos.util

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.equationl.paddleocr4android.OCR
import com.equationl.paddleocr4android.OcrConfig
import com.equationl.paddleocr4android.bean.OcrResult

class OCRManager(private val context: Context) {
    private val ocr = OCR(context)
    private var isInitialized = false

    fun init() {
        if (isInitialized) return
        
        val config = OcrConfig()
        config.modelPath = "models/ocr_v4"
        config.clsModelFilename = "cls.nb"
        config.detModelFilename = "det.nb"
        config.recModelFilename = "rec.nb"
        
        // We assume models are in assets/models/ocr_v4
        val result = ocr.initModelSync(config)
        result.onSuccess { success: Boolean ->
            isInitialized = success
            if (success) {
                Log.i("OCRManager", "OCR Model initialized successfully")
            } else {
                Log.e("OCRManager", "OCR Model initialization failed (returned false)")
            }
        }.onFailure { e: Throwable ->
            Log.e("OCRManager", "OCR Model initialization failed", e)
        }
    }

    fun runOCR(bitmap: Bitmap): String? {
        if (!isInitialized) {
            init()
        }
        if (!isInitialized) return null

        return try {
            val result = ocr.runSync(bitmap)
            val text = result.getOrNull()?.simpleText
            
            if (result.isSuccess) {
                if (text != null && text.isNotBlank()) {
                    Log.i("OCRManager", "OCR success: extracted $text")
                } else {
                    Log.d("OCRManager", "OCR finished: no text detected in image")
                }
            } else {
                Log.e("OCRManager", "OCR engine failed", result.exceptionOrNull())
            }
            
            text
        } catch (e: Exception) {
            Log.e("OCRManager", "Fatal error during OCR run", e)
            null
        }
    }

    fun release() {
        ocr.releaseModel()
        isInitialized = false
    }
}
