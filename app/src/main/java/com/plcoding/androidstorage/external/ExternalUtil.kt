package com.plcoding.androidstorage.external

import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.ContentValues
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.plcoding.androidstorage.sdk29AndUp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

suspend fun Activity.loadPhotosFromExternalStorage(): List<ExternalStoragePhoto> {
  return withContext(Dispatchers.IO) {
    val collection = sdk29AndUp { // Opposite to writes on external storage we can access to the whole external volume
      MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    } ?: MediaStore.Images.Media.EXTERNAL_CONTENT_URI

    val projection = arrayOf(
      MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.WIDTH, MediaStore.Images.Media.HEIGHT
    )

    val images = mutableListOf<ExternalStoragePhoto>()

    contentResolver.query(
      collection, projection, null, null, // Like the WHERE in sql
      "${MediaStore.Images.Media.DISPLAY_NAME} ASC"
    )?.use { cursor ->
      val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
      val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
      val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
      val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)

      while (cursor.moveToNext()) {
        val id = cursor.getLong(idColumn)
        val name = cursor.getString(nameColumn)
        val width = cursor.getInt(widthColumn)
        val height = cursor.getInt(heightColumn)
        val contentUri = ContentUris.withAppendedId(
          MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
        )
        images.add(ExternalStoragePhoto(id, name, width, height, contentUri))
      }
      images.toList()
    } ?: emptyList()
  }
}

suspend fun Activity.savePhotoToExternalStorage(fileName: String, bitmap: Bitmap): Boolean {
  return withContext(Dispatchers.IO) {
    val imageCollection = sdk29AndUp { // From android 29 and upwards we can only access to the primary external volume from external storage.
      MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    } ?: MediaStore.Images.Media.EXTERNAL_CONTENT_URI

    // Used to store metadata with the file
    val contentValues = ContentValues().apply {
      put(MediaStore.Images.Media.DISPLAY_NAME, "$fileName.jpg")
      put(MediaStore.Images.Media.MIME_TYPE, "image/jpg")
      put(MediaStore.Images.Media.WIDTH, bitmap.width)
      put(MediaStore.Images.Media.HEIGHT, bitmap.height)
    }

    try { // Does not save the image yet. This only saves the metadata, and gives us the uri where we can store the image.
      contentResolver.insert(imageCollection, contentValues)?.also { uri ->
        contentResolver.openOutputStream(uri).use { outputStream ->
          if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)) {
            throw IOException("Could not save image")
          }
        }
      } ?: throw IOException("Could not create media entry")
      true
    } catch (e: IOException) {
      e.printStackTrace()
      false
    }
  }
}

suspend fun Activity.deletePhotoFromExternalStorage(photoUri: Uri, intentSenderLauncher: ActivityResultLauncher<IntentSenderRequest>) {
  withContext(Dispatchers.IO) {
    try {
      contentResolver.delete(photoUri, null, null)
    } catch (e: SecurityException) {
      val intentSender = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
          MediaStore.createDeleteRequest(contentResolver, listOf(photoUri)).intentSender
        }
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
          val recoverableSecurityException = e as? RecoverableSecurityException
          recoverableSecurityException?.userAction?.actionIntent?.intentSender
        }
        else -> null
      }

      intentSender?.let { sender ->
        intentSenderLauncher.launch(
          IntentSenderRequest.Builder(sender).build()
        )
      }
    }
  }
}