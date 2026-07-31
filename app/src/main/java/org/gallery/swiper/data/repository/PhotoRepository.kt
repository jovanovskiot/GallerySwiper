package org.gallery.swiper.data.repository

import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.MediaStore.Images.Media
import android.provider.MediaStore.Video.Media as VideoMedia
import android.provider.MediaStore.Images.Thumbnails as ImageThumbnails
import android.provider.MediaStore.Video.Thumbnails as VideoThumbnails
import android.util.Log
import org.gallery.swiper.data.model.Photo
import org.gallery.swiper.data.model.PhotoMonth
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

class PhotoRepository private constructor(private val context: Context) {

    companion object {
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var INSTANCE: PhotoRepository? = null

        fun getInstance(context: Context): PhotoRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PhotoRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    @Volatile
    private var cachedMonths: List<PhotoMonth>? = null

    fun getPhotosByMonth(): List<PhotoMonth> {
        return cachedMonths ?: synchronized(this) {
            cachedMonths ?: groupByMonth(loadAllPhotos()).also { cachedMonths = it }
        }
    }

    fun invalidateCache() {
        cachedMonths = null
    }

    fun loadAllPhotos(): List<Photo> {
        val photos = mutableListOf<Photo>()
        var imagesFailed = false
        var videosFailed = false

        try { photos.addAll(loadImages()) } catch (e: Exception) {
            Log.e("PhotoRepository", "Failed to load images", e)
            imagesFailed = true
        }
        try { photos.addAll(loadVideos()) } catch (e: Exception) {
            Log.e("PhotoRepository", "Failed to load videos", e)
            videosFailed = true
        }

        if (photos.isEmpty() && (imagesFailed || videosFailed)) {
            throw RuntimeException("Failed to load photos and videos from MediaStore")
        }

        return photos
    }

    @Suppress("DEPRECATION")
    private fun loadImages(): List<Photo> {
        val photos = mutableListOf<Photo>()
        val resolver = context.contentResolver

        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            Media._ID,
            Media.DATE_TAKEN,
            Media.DATE_MODIFIED,
            Media.SIZE,
            Media.MIME_TYPE,
            Media.WIDTH,
            Media.HEIGHT,
        )

        val cursor = resolver.query(uri, projection, null, null, null)
            ?: throw RuntimeException("MediaStore images query returned null")

        cursor.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(Media._ID)
            val dateCol = cursor.getColumnIndexOrThrow(Media.DATE_TAKEN)
            val modifiedCol = cursor.getColumnIndex(Media.DATE_MODIFIED)
            val sizeCol = cursor.getColumnIndexOrThrow(Media.SIZE)
            val mimeCol = cursor.getColumnIndexOrThrow(Media.MIME_TYPE)
            val widthCol = cursor.getColumnIndex(Media.WIDTH)
            val heightCol = cursor.getColumnIndex(Media.HEIGHT)

            while (cursor.moveToNext()) {
                val dateTaken = cursor.getLong(dateCol)
                val dateModified = if (modifiedCol >= 0) cursor.getLong(modifiedCol) else 0L
                val date = if (dateTaken > 0) dateTaken else dateModified * 1000L
                val id = cursor.getLong(idCol)
                val photoUri = ContentUris.withAppendedId(uri, id)

                photos.add(
                    Photo(
                        id = id,
                        uri = photoUri,
                        dateTaken = date,
                        size = cursor.getLong(sizeCol),
                        mimeType = cursor.getString(mimeCol) ?: "image/*",
                        width = if (widthCol >= 0) cursor.getInt(widthCol) else 0,
                        height = if (heightCol >= 0) cursor.getInt(heightCol) else 0,
                    )
                )
            }
        }

        return applyThumbnails(photos, ImageThumbnails.EXTERNAL_CONTENT_URI, ImageThumbnails.IMAGE_ID)
    }

    private fun applyThumbnails(
        photos: List<Photo>,
        thumbnailsUri: Uri,
        idColumn: String,
    ): List<Photo> {
        if (photos.isEmpty()) return photos
        val thumbMap = loadThumbnailMap(photos.mapTo(HashSet()) { it.id }, thumbnailsUri, idColumn)
        if (thumbMap.isEmpty()) return photos
        return photos.map { photo ->
            val thumb = thumbMap[photo.id]
            if (thumb != null) photo.copy(thumbnailUri = thumb) else photo
        }
    }

    @Suppress("DEPRECATION")
    private fun loadVideos(): List<Photo> {
        val photos = mutableListOf<Photo>()
        val resolver = context.contentResolver

        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            VideoMedia.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            VideoMedia.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            VideoMedia._ID,
            VideoMedia.DATE_TAKEN,
            VideoMedia.DATE_MODIFIED,
            VideoMedia.SIZE,
            VideoMedia.MIME_TYPE,
            VideoMedia.WIDTH,
            VideoMedia.HEIGHT,
        )

        val cursor = resolver.query(uri, projection, null, null, null)
            ?: throw RuntimeException("MediaStore videos query returned null")

        cursor.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(VideoMedia._ID)
            val dateCol = cursor.getColumnIndexOrThrow(VideoMedia.DATE_TAKEN)
            val modifiedCol = cursor.getColumnIndex(VideoMedia.DATE_MODIFIED)
            val sizeCol = cursor.getColumnIndexOrThrow(VideoMedia.SIZE)
            val mimeCol = cursor.getColumnIndexOrThrow(VideoMedia.MIME_TYPE)
            val widthCol = cursor.getColumnIndex(VideoMedia.WIDTH)
            val heightCol = cursor.getColumnIndex(VideoMedia.HEIGHT)

            while (cursor.moveToNext()) {
                val dateTaken = cursor.getLong(dateCol)
                val dateModified = if (modifiedCol >= 0) cursor.getLong(modifiedCol) else 0L
                val date = if (dateTaken > 0) dateTaken else dateModified * 1000L
                val id = cursor.getLong(idCol)
                val videoUri = ContentUris.withAppendedId(uri, id)

                photos.add(
                    Photo(
                        id = id,
                        uri = videoUri,
                        dateTaken = date,
                        size = cursor.getLong(sizeCol),
                        mimeType = cursor.getString(mimeCol) ?: "video/*",
                        width = if (widthCol >= 0) cursor.getInt(widthCol) else 0,
                        height = if (heightCol >= 0) cursor.getInt(heightCol) else 0,
                    )
                )
            }
        }

        return applyThumbnails(photos, VideoThumbnails.EXTERNAL_CONTENT_URI, VideoThumbnails.VIDEO_ID)
    }

    @Suppress("DEPRECATION")
    private fun loadThumbnailMap(ids: Set<Long>, thumbnailsUri: Uri, idColumn: String): Map<Long, Uri> {
        if (ids.isEmpty()) return emptyMap()
        val result = HashMap<Long, Uri>()
        try {
            val projection = arrayOf(idColumn, ImageThumbnails._ID)
            context.contentResolver.query(thumbnailsUri, projection, null, null, null)?.use { cursor ->
                val ownerCol = cursor.getColumnIndexOrThrow(idColumn)
                val thumbCol = cursor.getColumnIndexOrThrow(ImageThumbnails._ID)
                while (cursor.moveToNext()) {
                    val ownerId = cursor.getLong(ownerCol)
                    if (ownerId in ids) {
                        result[ownerId] = ContentUris.withAppendedId(thumbnailsUri, cursor.getLong(thumbCol))
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("PhotoRepository", "Failed to load thumbnails", e)
        }
        return result
    }

    fun groupByMonth(photos: List<Photo>): List<PhotoMonth> {
        return photos
            .groupBy { photo ->
                val localDate = Instant.ofEpochMilli(photo.dateTaken)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                YearMonth.from(localDate)
            }
            .map { (yearMonth, photoList) ->
                PhotoMonth(
                    year = yearMonth.year,
                    month = yearMonth.monthValue,
                    photos = photoList.sortedByDescending { it.dateTaken },
                )
            }
            .sortedWith(compareByDescending<PhotoMonth> { it.year }.thenByDescending { it.month })
    }

    fun sendToTrash(uris: List<Uri>): Boolean {
        if (uris.isEmpty()) return true
        val resolver = context.contentResolver
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val trashRequest = MediaStore.createTrashRequest(resolver, uris, true)
                trashRequest.send()
                true
        } else {
            for (uri in uris) {
                if (resolver.delete(uri, null, null) == 0) {
                    Log.w("PhotoRepository", "resolver.delete returned 0 for $uri")
                    return false
                }
            }
            true
        }
        } catch (e: Exception) {
            Log.e("PhotoRepository", "Failed to trash files", e)
            false
        }
    }

    fun getDeletionPendingIntentSender(uris: List<Uri>): android.content.IntentSender? {
        if (uris.isEmpty()) return null
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val builder = MediaStore.createTrashRequest(context.contentResolver, uris, true)
                builder.intentSender
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("PhotoRepository", "Failed to create trash IntentSender", e)
            null
        }
    }
}
