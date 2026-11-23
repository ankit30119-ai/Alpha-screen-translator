package com.alpha.screentranslator.engine

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.alpha.screentranslator.model.SourceScript
import com.alpha.screentranslator.model.TranslatedBlock
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID

class OcrAndTranslationEngine {

    private val languageIdentifier = LanguageIdentification.getClient()

    private fun getRecognizer(script: SourceScript) = when (script) {
        SourceScript.AUTO_LATIN -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        SourceScript.JAPANESE -> TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
        SourceScript.KOREAN -> TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
        SourceScript.CHINESE -> TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        SourceScript.DEVANAGARI -> TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
    }

    suspend fun processBitmapToHindi(
        bitmap: Bitmap,
        script: SourceScript = SourceScript.AUTO_LATIN,
        cropRect: Rect? = null,
        onStatusUpdate: (String) -> Unit = {}
    ): Result<List<TranslatedBlock>> = withContext(Dispatchers.Default) {
        try {
            val targetBitmap = if (cropRect != null) {
                val safeRect = Rect(
                    cropRect.left.coerceIn(0, bitmap.width - 1),
                    cropRect.top.coerceIn(0, bitmap.height - 1),
                    cropRect.right.coerceIn(1, bitmap.width),
                    cropRect.bottom.coerceIn(1, bitmap.height)
                )
                if (safeRect.width() > 10 && safeRect.height() > 10) {
                    Bitmap.createBitmap(
                        bitmap,
                        safeRect.left,
                        safeRect.top,
                        safeRect.width(),
                        safeRect.height()
                    )
                } else bitmap
            } else {
                bitmap
            }

            onStatusUpdate("Scanning screen text...")
            val inputImage = InputImage.fromBitmap(targetBitmap, 0)
            val recognizer = getRecognizer(script)
            val visionText = recognizer.process(inputImage).await()

            if (visionText.textBlocks.isEmpty()) {
                return@withContext Result.success(emptyList())
            }

            onStatusUpdate("Detecting language & translating to Hindi...")
            val results = mutableListOf<TranslatedBlock>()
            val offsetX = cropRect?.left ?: 0
            val offsetY = cropRect?.top ?: 0

            for (block in visionText.textBlocks) {
                val rawText = block.text.trim()
                if (rawText.isBlank()) continue

                val box = block.boundingBox ?: Rect(0, 0, 100, 50)
                val adjustedRect = Rect(
                    box.left + offsetX,
                    box.top + offsetY,
                    box.right + offsetX,
                    box.bottom + offsetY
                )

                val (detectedLang, hindiTranslation) = translateSingleTextToHindi(
                    text = rawText,
                    fallbackScript = script,
                    onDownloadNotice = onStatusUpdate
                )

                results.add(
                    TranslatedBlock(
                        id = UUID.randomUUID().toString(),
                        originalText = rawText,
                        translatedHindiText = hindiTranslation,
                        boundingBox = adjustedRect,
                        detectedLanguageCode = detectedLang
                    )
                )
            }

            Result.success(results)
        } catch (e: Exception) {
            Log.e("AlphaOcrEngine", "OCR/Translation pipeline failed", e)
            Result.failure(e)
        }
    }

    suspend fun translateSingleTextToHindi(
        text: String,
        fallbackScript: SourceScript = SourceScript.AUTO_LATIN,
        onDownloadNotice: (String) -> Unit = {}
    ): Pair<String, String> = withContext(Dispatchers.IO) {
        try {
            val identifiedTag = languageIdentifier.identifyLanguage(text).await()
            val sourceLangCode = resolveMlKitLanguage(identifiedTag, fallbackScript)

            if (sourceLangCode == TranslateLanguage.HINDI) {
                return@withContext Pair("hi", text)
            }

            val options = TranslatorOptions.Builder()
                .setSourceLanguage(sourceLangCode)
                .setTargetLanguage(TranslateLanguage.HINDI)
                .build()

            val translator = Translation.getClient(options)
            val conditions = DownloadConditions.Builder().build()

            onDownloadNotice("Ensuring Hindi offline model is ready...")
            translator.downloadModelIfNeeded(conditions).await()

            val translated = translator.translate(text).await()
            translator.close()

            Pair(sourceLangCode, translated)
        } catch (e: Exception) {
            Log.e("AlphaTranslate", "Translation error for text: $text", e)
            Pair("unknown", "[अनुवाद त्रुटि: ${e.localizedMessage ?: "Check internet for 1st-time language pack"}]")
        }
    }

    private fun resolveMlKitLanguage(identifiedTag: String, fallbackScript: SourceScript): String {
        if (identifiedTag != "und") {
            val supported = TranslateLanguage.fromLanguageTag(identifiedTag)
            if (supported != null) return supported
        }
        return when (fallbackScript) {
            SourceScript.JAPANESE -> TranslateLanguage.JAPANESE
            SourceScript.KOREAN -> TranslateLanguage.KOREAN
            SourceScript.CHINESE -> TranslateLanguage.CHINESE
            SourceScript.DEVANAGARI -> TranslateLanguage.HINDI
            SourceScript.AUTO_LATIN -> TranslateLanguage.ENGLISH
        }
    }
}
