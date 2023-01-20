package com.plcoding.androidstorage

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.plcoding.androidstorage.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.*

class MainActivity : AppCompatActivity() {

  private lateinit var binding: ActivityMainBinding
  private lateinit var internalStoragePhotoAdapter: InternalStoragePhotoAdapter

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    binding = ActivityMainBinding.inflate(layoutInflater)
    setContentView(binding.root)

    internalStoragePhotoAdapter = InternalStoragePhotoAdapter {
      val isDeletionSuccessful = deletePhotoFromInternalStorage(it.name)
      if (isDeletionSuccessful) {
        loadPhotosFromInternalStorageIntoRecyclerView()
        Toast.makeText(this, "Photo was deleted", Toast.LENGTH_LONG).show()
      } else {
        Toast.makeText(this, "Photo was not deleted", Toast.LENGTH_LONG).show()
      }
    }

    val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) {
      val isPrivate = binding.switchPrivate.isChecked
      if (isPrivate) {
        val isSavedSuccessfully = savePhotoToInternalStorage(UUID.randomUUID().toString(), it)
        if (isSavedSuccessfully) {
          loadPhotosFromInternalStorageIntoRecyclerView()
          Toast.makeText(this, "Photo was saved", Toast.LENGTH_LONG).show()
        } else {
          Toast.makeText(this, "Photo was not saved", Toast.LENGTH_LONG).show()
        }
      }
    }

    binding.btnTakePhoto.setOnClickListener {
      takePhoto.launch()
    }

    setUpInternalStorageRecyclerView()
    loadPhotosFromInternalStorageIntoRecyclerView()
  }

  private fun setUpInternalStorageRecyclerView() = binding.rvPrivatePhotos.apply {
    adapter = internalStoragePhotoAdapter
    layoutManager = StaggeredGridLayoutManager(3, RecyclerView.VERTICAL)
  }

  private fun loadPhotosFromInternalStorageIntoRecyclerView() {
    lifecycleScope.launch {
      val photos = loadPhotoFromInternalStorage()
      internalStoragePhotoAdapter.submitList(photos)
    }
  }

  private fun deletePhotoFromInternalStorage(fileName: String): Boolean {
    return try {
      deleteFile(fileName)
    } catch (e: Exception) {
      e.printStackTrace()
      return false
    }
  }

  private suspend fun loadPhotoFromInternalStorage(): List<InternalStoragePhoto> {
    return withContext(Dispatchers.IO) {
      val files = filesDir.listFiles()
      files?.filter { it.canRead() && it.isFile && it.name.endsWith(".jpg") }?.map {
        val bytes = it.readBytes()
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        InternalStoragePhoto(it.name, bitmap)
      } ?: listOf()
    }
  }

  private fun savePhotoToInternalStorage(filename: String, bmp: Bitmap): Boolean {
    return try {
      // Use close the stream when the block is finished
      openFileOutput("$filename.jpg", MODE_PRIVATE).use { stream ->
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