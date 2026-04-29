package com.vayunmathur.youpipe.util

import android.content.Context
import com.vayunmathur.library.util.DatabaseViewModel
import com.vayunmathur.youpipe.data.DownloadedVideo
import com.vayunmathur.youpipe.ui.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink
import java.io.File
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock

suspend fun downloadVideo(
    context: Context,
    viewModel: DatabaseViewModel,
    videoInfo: VideoInfo,
    videoUrl: String,
    audioUrl: String? = null
) {
    withContext(Dispatchers.IO) {
        val client = OkHttpClient()
        
        val dir = File(context.getExternalFilesDir(null), "downloads")
        if (!dir.exists()) dir.mkdirs()
        
        val videoFile = File(dir, "${videoInfo.videoID}.mp4")
        val audioFile = if (audioUrl != null) File(dir, "${videoInfo.videoID}.m4a") else null
        
        try {
            val videoWeight = if (audioUrl != null) 0.5 else 1.0
            downloadFileWithProgress(coroutineContext, client, videoUrl, videoFile) { p ->
                DownloadManager.updateProgress(videoInfo.videoID, p * videoWeight)
            }
            
            audioUrl?.let { url ->
                downloadFileWithProgress(coroutineContext, client, url, audioFile!!) { p ->
                    DownloadManager.updateProgress(videoInfo.videoID, 0.5 + (p * 0.5))
                }
            }
            
            val download = DownloadedVideo(
                id = videoInfo.videoID,
                videoItem = videoInfo,
                filePath = videoFile.absolutePath,
                audioPath = audioFile?.absolutePath,
                timestamp = Clock.System.now()
            )
            
            viewModel.upsert(download)
        } catch (e: Exception) {
            // Cleanup on error or cancellation
            videoFile.delete()
            audioFile?.delete()
            throw e
        } finally {
            DownloadManager.finishDownload(videoInfo.videoID)
        }
    }
}

private fun downloadFileWithProgress(
    coroutineContext: CoroutineContext,
    client: OkHttpClient,
    url: String,
    file: File,
    onProgress: (Double) -> Unit
) {
    val request = Request.Builder().url(url).build()
    client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) return
        val totalBytes = response.body.contentLength()
        var downloadedBytes = 0L
        
        val source = response.body.source()
        val sink = file.sink().buffer()
        
        val buffer = okio.Buffer()
        while (coroutineContext.isActive) {
            val read = source.read(buffer, 8192)
            if (read == -1L) break
            
            sink.write(buffer, read)
            downloadedBytes += read
            if (totalBytes > 0) {
                onProgress(downloadedBytes.toDouble() / totalBytes.toDouble())
            }
        }
        sink.flush()
        sink.close()
        
        if (!coroutineContext.isActive) {
            file.delete()
        }
    }
}
