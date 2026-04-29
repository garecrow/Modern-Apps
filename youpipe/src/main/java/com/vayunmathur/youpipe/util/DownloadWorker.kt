package com.vayunmathur.youpipe.util

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.vayunmathur.library.util.buildDatabase
import com.vayunmathur.youpipe.data.DownloadedVideo
import com.vayunmathur.youpipe.data.SubscriptionDatabase
import com.vayunmathur.youpipe.ui.VideoInfo
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink
import java.io.File
import kotlin.time.Clock
import kotlin.time.Instant

class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val videoID = inputData.getLong("videoID", -1L)
        val videoUrl = inputData.getString("videoUrl") ?: return Result.failure()
        val audioUrl = inputData.getString("audioUrl")
        
        val videoInfo = VideoInfo(
            name = inputData.getString("name") ?: "",
            videoID = videoID,
            duration = inputData.getLong("duration", 0L),
            views = inputData.getLong("views", 0L),
            uploadDate = Instant.fromEpochSeconds(inputData.getLong("uploadDate", 0L)),
            thumbnailURL = inputData.getString("thumbnailURL") ?: "",
            author = inputData.getString("author") ?: ""
        )

        val db = applicationContext.buildDatabase<SubscriptionDatabase>()
        
        return try {
            val client = OkHttpClient()
            val dir = File(applicationContext.getExternalFilesDir(null), "downloads")
            if (!dir.exists()) dir.mkdirs()

            val videoFile = File(dir, "$videoID.mp4")
            val audioFile = if (audioUrl != null) File(dir, "$videoID.m4a") else null

            val videoWeight = if (audioUrl != null) 0.5 else 1.0
            
            downloadFile(client, videoUrl, videoFile) { p ->
                DownloadManager.updateProgress(videoID, p * videoWeight)
            }

            audioUrl?.let { url ->
                downloadFile(client, url, audioFile!!) { p ->
                    DownloadManager.updateProgress(videoID, 0.5 + (p * 0.5))
                }
            }

            val download = DownloadedVideo(
                id = videoID,
                videoItem = videoInfo,
                filePath = videoFile.absolutePath,
                audioPath = audioFile?.absolutePath,
                timestamp = Clock.System.now()
            )

            db.downloadedVideoDao().upsert(download)
            Result.success()
        } catch (e: Exception) {
            Result.failure()
        } finally {
            DownloadManager.finishDownload(videoID)
        }
    }

    private fun downloadFile(client: OkHttpClient, url: String, file: File, onProgress: (Double) -> Unit) {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return
            val totalBytes = response.body.contentLength()
            var downloadedBytes = 0L

            val source = response.body.source()
            val sink = file.sink().buffer()

            val buffer = okio.Buffer()
            while (true) {
                if (isStopped) {
                    sink.close()
                    file.delete()
                    return
                }
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
        }
    }

    companion object {
        fun enqueue(context: Context, videoInfo: VideoInfo, videoUrl: String, audioUrl: String?) {
            val data = Data.Builder()
                .putLong("videoID", videoInfo.videoID)
                .putString("name", videoInfo.name)
                .putLong("duration", videoInfo.duration)
                .putLong("views", videoInfo.views)
                .putLong("uploadDate", videoInfo.uploadDate.epochSeconds)
                .putString("thumbnailURL", videoInfo.thumbnailURL)
                .putString("author", videoInfo.author)
                .putString("videoUrl", videoUrl)
                .putString("audioUrl", audioUrl)
                .build()

            val request = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(data)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "download_${videoInfo.videoID}",
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }
}
