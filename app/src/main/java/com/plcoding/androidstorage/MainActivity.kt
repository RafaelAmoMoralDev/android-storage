package com.plcoding.androidstorage

import android.Manifest
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.plcoding.androidstorage.databinding.ActivityMainBinding
import com.plcoding.androidstorage.internal.*
import com.plcoding.androidstorage.external.ExternalPhotoAdapter
import com.plcoding.androidstorage.external.deletePhotoFromExternalStorage
import com.plcoding.androidstorage.external.loadPhotosFromExternalStorage
import com.plcoding.androidstorage.external.savePhotoToExternalStorage
import kotlinx.coroutines.launch
import java.util.*

class MainActivity : AppCompatActivity() {

  private lateinit var binding: ActivityMainBinding
  private lateinit var internalStoragePhotoAdapter: InternalStoragePhotoAdapter
  private lateinit var externalStoragePhotoAdapter: ExternalPhotoAdapter
  // Observer who return if a change on a specific directory was made.
  private lateinit var contentObserver: ContentObserver

  private var readPermissionsGranted: Boolean = false
  private var writePermissionsGranted: Boolean = false
  // On Android 11 it will show the modal only a couple of times. Multiple revokes on Android 11 and upwards equals to do not ask me again.
  private val permissionsLauncher: ActivityResultLauncher<Array<String>> = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
    readPermissionsGranted = permissions[Manifest.permission.READ_EXTERNAL_STORAGE] ?: readPermissionsGranted
    writePermissionsGranted = permissions[Manifest.permission.WRITE_EXTERNAL_STORAGE] ?: writePermissionsGranted
  }
  private lateinit var intentSenderLauncher: ActivityResultLauncher<IntentSenderRequest>
  private var deletedImageUri: Uri? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    binding = ActivityMainBinding.inflate(layoutInflater)
    setContentView(binding.root)

    updateOrRequestPermissions()

    setUpInternalStorageList()
    loadPhotosFromInternalStorageIntoRecyclerView()

    setUpExternalStorageList()
    loadPhotosFromExternalStorageIntoRecyclerView()
    initContentObserver()
    intentSenderLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
      if (it.resultCode == RESULT_OK) {
        // On API 29 once you allow the image deletion the system does not remove it, the user should perform again the remove action, since
        // this is an unexpected behaviour, we emulate the double action.
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
          lifecycleScope.launch {
            if (deletedImageUri != null) {
              deletePhotoFromExternalStorage(deletedImageUri!!, intentSenderLauncher)
            }
          }
        }
        Toast.makeText(this@MainActivity, "Photo deleted successfully", Toast.LENGTH_LONG).show()
      } else {
        Toast.makeText(this@MainActivity, "Photo not deleted", Toast.LENGTH_LONG).show()
      }
    }

    val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) {
      val isPrivate = binding.switchPrivate.isChecked

      lifecycleScope.launch {
        val isSavedSuccessfully = when {
          isPrivate -> savePhotoToInternalStorage(UUID.randomUUID().toString(), it)
          writePermissionsGranted -> savePhotoToExternalStorage(UUID.randomUUID().toString(), it)
          else -> false
        }

        if (isSavedSuccessfully) {
          if (isPrivate) {
            loadPhotosFromInternalStorageIntoRecyclerView()
          }
          Toast.makeText(this@MainActivity, "Photo was saved", Toast.LENGTH_LONG).show()
        } else {
          Toast.makeText(this@MainActivity, "Photo was not saved", Toast.LENGTH_LONG).show()
        }
      }
    }

    binding.btnTakePhoto.setOnClickListener {
      takePhoto.launch()
    }
  }

  private fun updateOrRequestPermissions() {
    val hasReadPermissions = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    val hasWritePermissions = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    /**
     * Check if the app can writes on the shared storage
     *
     * On Android 10 (API 29), we can add media to MediaStore without having to request the
     * [WRITE_EXTERNAL_STORAGE] permission, so we only check on pre-API 29 devices
     */
    val sdk29AndAbove = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    readPermissionsGranted = hasReadPermissions
    writePermissionsGranted = hasWritePermissions || sdk29AndAbove

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

  private fun initContentObserver() {
    contentObserver = object : ContentObserver(null) {
      override fun onChange(selfChange: Boolean) {
        if (readPermissionsGranted) {
          loadPhotosFromExternalStorageIntoRecyclerView()
        }
      }
    }
    contentResolver.registerContentObserver(
      MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
      true,
      contentObserver
    )
  }

  private fun setUpInternalStorageList() = binding.rvPrivatePhotos.apply {
    internalStoragePhotoAdapter = InternalStoragePhotoAdapter {
      lifecycleScope.launch {
        val isDeletionSuccessful = deletePhotoFromInternalStorage(it.name)
        if (isDeletionSuccessful) {
          loadPhotosFromInternalStorageIntoRecyclerView()
          Toast.makeText(this@MainActivity, "Photo was deleted", Toast.LENGTH_LONG).show()
        } else {
          Toast.makeText(this@MainActivity, "Photo was not deleted", Toast.LENGTH_LONG).show()
        }
      }
    }
    adapter = internalStoragePhotoAdapter
    layoutManager = StaggeredGridLayoutManager(3, RecyclerView.VERTICAL)
  }

  private fun setUpExternalStorageList() = binding.rvExternalPhotos.apply {
    if (readPermissionsGranted) {
      externalStoragePhotoAdapter = ExternalPhotoAdapter {
        lifecycleScope.launch {
          deletePhotoFromExternalStorage(it.contentUri, intentSenderLauncher)
          deletedImageUri = it.contentUri
        }
      }
      adapter = externalStoragePhotoAdapter
      layoutManager = StaggeredGridLayoutManager(3, RecyclerView.VERTICAL)
    }
  }

  private fun loadPhotosFromInternalStorageIntoRecyclerView() {
    lifecycleScope.launch {
      val photos = loadPhotoFromInternalStorage()
      internalStoragePhotoAdapter.submitList(photos)
    }
  }

  private fun loadPhotosFromExternalStorageIntoRecyclerView() {
    // It is required for external storage to grant read permissions
    if (readPermissionsGranted) {
      lifecycleScope.launch {
        val photos = loadPhotosFromExternalStorage()
        externalStoragePhotoAdapter.submitList(photos)
      }
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    contentResolver.unregisterContentObserver(contentObserver)
  }

}