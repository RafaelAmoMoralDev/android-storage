package com.plcoding.androidstorage

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.core.content.ContextCompat
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
  private lateinit var externalStoragePhotoAdapter: SharedPhotoAdapter

  private var readPermissionsGranted: Boolean = false
  private var writePermissionsGranted: Boolean = false
  // On Android 11 it will show the modal only a couple of times. Multiple revokes on Android 11 and upwards equals to do not ask me again.
  private val permissionsLauncher: ActivityResultLauncher<Array<String>> = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
    readPermissionsGranted = permissions[Manifest.permission.READ_EXTERNAL_STORAGE] ?: readPermissionsGranted
    writePermissionsGranted = permissions[Manifest.permission.WRITE_EXTERNAL_STORAGE] ?: writePermissionsGranted
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    binding = ActivityMainBinding.inflate(layoutInflater)
    setContentView(binding.root)

    updateOrRequestPermissions()

    setUpInternalStorageList()
    loadPhotosFromInternalStorageIntoRecyclerView()

    externalStoragePhotoAdapter = SharedPhotoAdapter {

    }

    val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) {
      val isPrivate = binding.switchPrivate.isChecked

      val isSavedSuccessfully = when {
        isPrivate -> savePhotoToInternalStorage(UUID.randomUUID().toString(), it)
        writePermissionsGranted -> savePhotoToExternalStorage(UUID.randomUUID().toString(), it)
        else -> false
      }

      if (isSavedSuccessfully) {
        if (isPrivate) {
          loadPhotosFromInternalStorageIntoRecyclerView()
        }
        Toast.makeText(this, "Photo was saved", Toast.LENGTH_LONG).show()
      } else {
        Toast.makeText(this, "Photo was not saved", Toast.LENGTH_LONG).show()
      }
    }

    binding.btnTakePhoto.setOnClickListener {
      takePhoto.launch()
    }
  }

  private fun updateOrRequestPermissions() {
    val hasReadPermissions = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    val hasWritePermissions = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    val atLeastSdk29 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    readPermissionsGranted = hasReadPermissions
    writePermissionsGranted = hasWritePermissions || atLeastSdk29

    val permissionsToRequest = mutableListOf<String>().apply {
      if (!readPermissionsGranted) {
        add(Manifest.permission.READ_EXTERNAL_STORAGE)
      }
      if (!writePermissionsGranted) {
        add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
      }
    }

    if (permissionsToRequest.isNotEmpty()) {
      permissionsLauncher.launch(permissionsToRequest.toTypedArray())
    }
  }

  private fun savePhotoToExternalStorage(fileName: String, bitmap: Bitmap): Boolean {
    val imageCollection = sdk29AndUp {
      // From android 29 and upwards we can only access to the primary external volume from external storage.
      MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    }?: MediaStore.Images.Media.EXTERNAL_CONTENT_URI

    // Used to store metadata with the file
    val contentValues = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "$fileName.jpg")
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpg")
        put(MediaStore.Images.Media.WIDTH, bitmap.width)
        put(MediaStore.Images.Media.HEIGHT, bitmap.height)
    }

    return try {
      // Does not save the image yet. This only saves the metadata, and gives us the uri where we can store the image.
      contentResolver.insert(imageCollection, contentValues)?.also { uri ->
        contentResolver.openOutputStream(uri).use { outputStream ->
          if(!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)) {
            throw IOException("Could not save image")
          }
        }
      }?: throw IOException("Could not create media entry")
       true
    } catch (e: IOException) {
      e.printStackTrace()
      false
    }

  }

  private fun setUpInternalStorageList() = binding.rvPrivatePhotos.apply {
    internalStoragePhotoAdapter = InternalStoragePhotoAdapter {
      val isDeletionSuccessful = deletePhotoFromInternalStorage(it.name)
      if (isDeletionSuccessful) {
        loadPhotosFromInternalStorageIntoRecyclerView()
        Toast.makeText(this@MainActivity, "Photo was deleted", Toast.LENGTH_LONG).show()
      } else {
        Toast.makeText(this@MainActivity, "Photo was not deleted", Toast.LENGTH_LONG).show()
      }
    }
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
      false
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