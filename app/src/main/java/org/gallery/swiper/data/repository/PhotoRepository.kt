package org.gallery.swiper.data.repository

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.MediaStore.Images.Media
import android.provider.MediaStore.Video.Media as VideoMedia
import android.util.Log
import org.gallery.swiper.data.model.Photo
import org.gallery.swiper.data.model.PhotoMonth
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

class PhotoRepository(private val context: Context) {

    fun getPhotosByMonth(): List<PhotoMonth> {
        val photos = loadAllPhotos()
        return groupByMonth(photos)
    }

    fun loadAllPhotos(): List<Photo> {
        val photos = mutableListOf<Photo>()
        photos.addAll(loadImages())
        photos.addAll(loadVideos())
        return photos.sortedByDescending { it.dateTaken }
    }

    private fun loadImages(): List<Photo> {
        val photos = mutableListOf<Photo>()
        val resolver = context.contentResolver

        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            Media._ID,
            Media.DATE_TAKEN,
            Media.DATE_MODIFIED,
            Media.SIZE,
            Media.MIME_TYPE,
            Media.WIDTH,
            Media.HEIGHT,
            Media.LATITUDE,
            Media.LONGITUDE,
        )

        try {
            resolver.query(uri, projection, null, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(Media._ID)
                val dateCol = cursor.getColumnIndexOrThrow(Media.DATE_TAKEN)
                val modifiedCol = cursor.getColumnIndex(Media.DATE_MODIFIED)
                val sizeCol = cursor.getColumnIndexOrThrow(Media.SIZE)
                val mimeCol = cursor.getColumnIndexOrThrow(Media.MIME_TYPE)
                val widthCol = cursor.getColumnIndex(Media.WIDTH)
                val heightCol = cursor.getColumnIndex(Media.HEIGHT)
                val latCol = cursor.getColumnIndex(Media.LATITUDE)
                val lonCol = cursor.getColumnIndex(Media.LONGITUDE)

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
                            latitude = if (latCol >= 0 && !cursor.isNull(latCol)) cursor.getDouble(latCol) else null,
                            longitude = if (lonCol >= 0 && !cursor.isNull(lonCol)) cursor.getDouble(lonCol) else null,
                        )
                    )
                }
            } ?: Log.w("PhotoRepository", "MediaStore images query returned null")
        } catch (e: Exception) {
            Log.e("PhotoRepository", "Failed to load images", e)
        }

        return photos
    }

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

        try {
            resolver.query(uri, projection, null, null, null)?.use { cursor ->
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
            } ?: Log.w("PhotoRepository", "MediaStore videos query returned null")
        } catch (e: Exception) {
            Log.e("PhotoRepository", "Failed to load videos", e)
        }

        return photos
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
                    resolver.delete(uri, null, null)
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
