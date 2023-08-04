package com.example.imagecapturetask.ui.view

import android.Manifest
import android.content.ContentValues
import android.content.ContentValues.TAG
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCaptureSession.CaptureCallback
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.media.Image
import android.media.ImageReader
import android.media.ImageReader.OnImageAvailableListener
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.util.SparseIntArray
import android.view.Surface
import android.view.TextureView
import android.widget.ImageButton
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.databinding.DataBindingUtil
import com.example.imagecapturetask.R
import com.example.imagecapturetask.databinding.ActivityCustomCameraBinding
import com.example.imagecapturetask.ui.model.ImageData
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class CustomCameraActivity : AppCompatActivity() {

    private lateinit var textureView: TextureView
    private lateinit var switchButton: ImageButton
    private lateinit var flashButton: ImageButton
    private lateinit var captureButton: ImageButton
    private lateinit var binding: ActivityCustomCameraBinding

    val orientations: SparseIntArray = SparseIntArray(4).apply {
        append(Surface.ROTATION_0, 0)
        append(Surface.ROTATION_90, 90)
        append(Surface.ROTATION_180, 180)
        append(Surface.ROTATION_270, 270)
    }


    private var cameraManager: CameraManager? = null
    private var cameraId: String? = null
    private var cameraDevice: CameraDevice? = null
    private var captureRequestBuilder: CaptureRequest.Builder? = null
    private var cameraCaptureSession: CameraCaptureSession? = null
    private var characteristics: CameraCharacteristics? = null
    private var wideAngleCameraId: String? = null
    private lateinit var previewSize: Size
    private lateinit var imageList: ArrayList<ImageData>
    private lateinit var editor: SharedPreferences.Editor
    private var zoomRatio: Float = 1.0f

    private var isFrontCamera = false
    private var isFlashOn = false


    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_custom_camera)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_custom_camera)

        val sharedPreference = getSharedPreferences("PREFERENCE_NAME", Context.MODE_PRIVATE)
        editor = sharedPreference.edit()
        imageList = ArrayList()

        try {
            val gsonobj = sharedPreference.getString("ImageList", "") ?: ""
            if (!gsonobj.isEmpty()) {
                val gson = Gson()
                val type = object : TypeToken<List<ImageData?>?>() {}.type
                imageList = gson.fromJson(gsonobj, type)
                Toast.makeText(this, "Size of images" + imageList.size, Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        textureView = findViewById(R.id.textureView)
        switchButton = findViewById(R.id.switchButton)
        flashButton = findViewById(R.id.flashButton)
        captureButton = findViewById(R.id.captureImage)


        // Set up camera switch button
        switchButton.setOnClickListener {
            isFrontCamera = !isFrontCamera
            closeCamera()
            openCamera()
        }

        // Set up flash button
        flashButton.setOnClickListener {
            isFlashOn = !isFlashOn
            updateFlashButtonIcon()
            updateCaptureRequest()
        }

        captureButton.setOnClickListener {
            //  Toast.makeText(this,"Entered capturing",Toast.LENGTH_SHORT).show()
            Log.d("ImageTask", "Entered capturing")

            //  captureImage()
            takePicture()
        }
        binding.camWidex.setOnClickListener {
            zoomRatio = 0.6f
            setUpWideAngleCamera()
        }
        binding.camNormalx.setOnClickListener {
            zoomRatio = 1.0f
            createCameraPreviewSession()
        }
        binding.camZoomx.setOnClickListener {
            zoomRatio = 2.0f
            createCameraPreviewSession()
        }
        textureView.surfaceTextureListener = surfaceTextureListener
    }

    override fun onResume() {
        super.onResume()
        openCamera()
    }

    override fun onPause() {
        super.onPause()
        closeCamera()
    }
// Inside your CustomCameraActivity class

    protected fun takePicture() {
        if (null == cameraDevice) {
            Log.e(TAG, "cameraDevice is null")
            return
        }
        val manager = getSystemService(CAMERA_SERVICE) as CameraManager
        try {
            val characteristics = manager.getCameraCharacteristics(cameraDevice!!.id)
            var jpegSizes: Array<Size>? = null
            jpegSizes =
                characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
                    .getOutputSizes(ImageFormat.JPEG)
            var width = previewSize.width
            var height = previewSize.height
            if (jpegSizes != null && 0 < jpegSizes.size) {
                width = jpegSizes[0].width
                height = jpegSizes[0].height
            }
            val reader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1)
            val outputSurfaces: MutableList<Surface> = ArrayList(2)
            outputSurfaces.add(reader.surface)
            outputSurfaces.add(Surface(textureView.surfaceTexture))
            val captureBuilder =
                cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            captureBuilder.addTarget(reader.surface)
            captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
            captureBuilder.let { setZoomLevel(it, zoomRatio) }
            // Orientation
            val rotation = windowManager.defaultDisplay.rotation
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, orientations.get(rotation))
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())

            val file =
                File(Environment.DIRECTORY_DCIM + "/ImageCaptureTask" + "/JPEG_${timeStamp}.jpg")
            val readerListener: OnImageAvailableListener = object : OnImageAvailableListener {
                @RequiresApi(Build.VERSION_CODES.Q)
                override fun onImageAvailable(reader: ImageReader) {
                    var image: Image? = null
                    try {
                        image = reader.acquireLatestImage()
                        val buffer = image.planes[0].buffer
                        val bytes = ByteArray(buffer.capacity())
                        buffer[bytes]
                        save(bytes)
                    } catch (e: FileNotFoundException) {
                        e.printStackTrace()
                    } catch (e: IOException) {
                        e.printStackTrace()
                    } finally {
                        image?.close()
                    }
                }

                @RequiresApi(Build.VERSION_CODES.Q)
                @Throws(IOException::class)
                private fun save(bytes: ByteArray) {
                    val bitmap = bytesToBitmap(bytes)
                    val timeStamp =
                        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                    val displayName = "JPEG_${timeStamp}" + "${zoomRatio}.jpg"
                    saveImage(bitmap, displayName)

                }
            }
            reader.setOnImageAvailableListener(readerListener, null)
            val captureListener: CaptureCallback = object : CaptureCallback() {
                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult
                ) {
                    super.onCaptureCompleted(session, request, result)
                    createCameraPreviewSession()
                }
            }
            cameraDevice!!.createCaptureSession(
                outputSurfaces,
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        try {
                            session.capture(
                                captureBuilder.build(),
                                captureListener,
                                null
                            )
                        } catch (e: CameraAccessException) {
                            e.printStackTrace()
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {}
                },
                null
            )
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    fun bytesToBitmap(imageBytes: ByteArray): Bitmap {
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    fun saveImage(bitmap: Bitmap, displayName: String) {
        val imageCollection =
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

        val imageDetails = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        }

        try {
            val contentUri = contentResolver.insert(imageCollection, imageDetails)
            contentUri?.let { uri ->
                val outputStream: OutputStream? = contentResolver.openOutputStream(uri)
                outputStream?.use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
                    outputStream.flush()
                    val imageData = ImageData(uri.toString(), displayName)
                    imageList.add(imageData)
                    val str = Gson().toJson(imageList)
                    editor.putString("ImageList", str)
                    editor.commit()
                    Toast.makeText(
                        this@CustomCameraActivity,
                        "Saved:${uri.path}.",
                        Toast.LENGTH_SHORT
                    ).show()
                    this.finish()
                }
            }
        } catch (e: IOException) {
            // Handle IO exception
        }
    }

    private fun updateFlashButtonIcon() {
        flashButton.setImageResource(if (isFlashOn) R.drawable.ic_flash_on else R.drawable.ic_flash_off)
    }

    private fun updateCaptureRequest() {
        captureRequestBuilder?.set(
            CaptureRequest.FLASH_MODE,
            if (isFlashOn) CaptureRequest.FLASH_MODE_TORCH else CaptureRequest.FLASH_MODE_OFF
        )
        captureRequestBuilder?.build()
            ?.let { cameraCaptureSession?.setRepeatingRequest(it, null, null) }
    }

    private val surfaceTextureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            openCamera()
        }

        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
            // Handle surface size change if needed
        }

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
            return true
        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
            // Handle surface texture updates if needed
        }
    }

    private fun openCamera() {
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        cameraId = getCameraId()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val maxZoom = characteristics?.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)
            Log.d("Zoom ranges", "Lower : " + maxZoom?.lower + " Higher :" + maxZoom?.upper)
            maxZoom?.let {
                println(it.toString())
            }
        }



        if (cameraId != null) {
            try {
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    cameraManager?.openCamera(cameraId!!, cameraStateCallback, null)
                } else {
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.CAMERA),
                        CAMERA_PERMISSION_REQUEST
                    )
                }
            } catch (e: CameraAccessException) {
                // Handle camera access exception
            }
        }
    }

    private fun getCameraId(): String? {
        try {
            for (id in cameraManager!!.cameraIdList) {
                characteristics = cameraManager?.getCameraCharacteristics(id)
                val facing = characteristics?.get(CameraCharacteristics.LENS_FACING)
                if (facing != null && (facing == CameraCharacteristics.LENS_FACING_FRONT && isFrontCamera) ||
                    (facing == CameraCharacteristics.LENS_FACING_BACK && !isFrontCamera)
                ) {
                    return id
                }
            }
        } catch (e: CameraAccessException) {
            // Handle camera access exception
        }
        return null
    }

    private val cameraStateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            cameraDevice = camera
            createCameraPreviewSession()
        }

        override fun onDisconnected(camera: CameraDevice) {
            cameraDevice?.close()
            cameraDevice = null
        }

        override fun onError(camera: CameraDevice, error: Int) {
            onDisconnected(camera)
            finish()
        }
    }

    private fun setUpWideAngleCamera() {
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager

        for (cameraId in cameraManager!!.cameraIdList) {
            val characteristics = cameraManager?.getCameraCharacteristics(cameraId)
            val lensFacing = characteristics?.get(CameraCharacteristics.LENS_FACING)

            if (lensFacing == CameraCharacteristics.LENS_FACING_BACK) {
                //Toast.makeText(this,"Wide angle camera setup"+characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.size,Toast.LENGTH_SHORT).show()
                val isWideAngle =
                    (characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.size
                        ?: 0) > 1
                if (isWideAngle) {
                    wideAngleCameraId = cameraId
                    openWideAngleCamera()
                    break
                }
            }
        }
    }

    private fun openWideAngleCamera() {
        wideAngleCameraId?.let {
            try {
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    //  Toast.makeText(this,"Wide angle camera",Toast.LENGTH_SHORT).show()
                    cameraManager?.openCamera(it, cameraStateCallback, null)
                } else {
                    // Handle camera permission not granted
                }
            } catch (e: CameraAccessException) {
                // Handle camera access exception
            }
        }
    }

    private fun createCameraPreviewSession() {
        try {
            val texture = textureView.surfaceTexture
            previewSize =
                characteristics?.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
                    .getOutputSizes(ImageFormat.JPEG).maxByOrNull { it.height * it.width }!!
            texture?.setDefaultBufferSize(previewSize.width, previewSize.height)
            val surface = Surface(texture)
            captureRequestBuilder =
                cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            captureRequestBuilder?.addTarget(surface)

            captureRequestBuilder?.let { setZoomLevel(it, zoomRatio) }

            cameraDevice?.createCaptureSession(
                listOf(surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        if (cameraDevice == null) return
                        cameraCaptureSession = session
                        updateCaptureRequest()
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        // Handle session configuration failure
                    }
                },
                null
            )
        } catch (e: CameraAccessException) {
            // Handle camera access exception
        }
    }

    private fun closeCamera() {
        cameraCaptureSession?.close()
        cameraCaptureSession = null
        cameraDevice?.close()
        cameraDevice = null
    }

    companion object {
        private const val CAMERA_PERMISSION_REQUEST = 100
    }

    private fun setZoomLevel(captureRequestBuilder: CaptureRequest.Builder, zoomRatio: Float) {
        val characteristics = cameraManager?.getCameraCharacteristics(cameraId!!)
        val maxZoom = characteristics?.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)
        val rect = characteristics?.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)

        //  val zoomRatio = maxZoom?.times(zoomLevel)
        val width = (rect!!.width() / zoomRatio).toInt()
        val height = (rect.height() / zoomRatio).toInt()
        val centerX = rect.centerX()
        val centerY = rect.centerY()
        val zoomRect = Rect(
            centerX - width / 2,
            centerY - height / 2,
            centerX + width / 2,
            centerY + height / 2
        )

        captureRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, zoomRect)
    }
}