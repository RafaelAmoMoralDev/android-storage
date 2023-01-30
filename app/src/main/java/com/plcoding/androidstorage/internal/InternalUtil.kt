package com.plcoding.androidstorage.internal

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

suspend fun Activity.loadPhotoFromInternalStorage(): List<InternalStoragePhoto> {
  return withContext(Dispatchers.IO) {
    val files = filesDir.listFiles()
    files?.filter { it.canRead() && it.isFile && it.name.endsWith(".jpg") }?.map {
      val bytes = it.readBytes()
      val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
      InternalStoragePhoto(it.name, bitmap)
    } ?: listOf()
  }
}

suspend fun Activity.savePhotoToInternalStorage(filename: String, bmp: Bitmap): Boolean {
  return withContext(Dispatchers.IO) {
    try { // Use close the stream when the block is finished
      openFileOutput("$filename.jpg", AppCompatActivity.MODE_PRIVATE).use { stream ->
        if (!bmp.compress(Bitmap.CompressFormat.JPEG, 95, stream)) {
          throw IOException("Could not save Bitmap")
        }
      }
      true
    } catch (e: IOException) {
      e.printStackTrace()
      false
    }
  }
}

suspend fun Activity.deletePhotoFromInternalStorage(fileName: String): Boolean {
  return withContext(Dispatchers.IO) {
    try {
      deleteFile(fileName)
    } catch (e: Exception) {
      e.printStackTrace()
      false
    }
  }
}