package com.richarddewan.camerax.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.hardware.Camera
import android.hardware.display.DisplayManager
import android.location.LocationManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import androidx.camera.core.*
import androidx.camera.core.impl.utils.Exif
import androidx.camera.extensions.BeautyImageCaptureExtender
import androidx.camera.extensions.BokehImageCaptureExtender
import androidx.camera.extensions.HdrImageCaptureExtender
import androidx.camera.extensions.NightImageCaptureExtender
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toFile
import androidx.databinding.DataBindingUtil
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.afollestad.materialdialogs.MaterialDialog
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.gms.tasks.Task
import com.google.android.material.snackbar.Snackbar
import com.google.common.util.concurrent.ListenableFuture
import com.richarddewan.camerax.R
import com.richarddewan.camerax.databinding.FragmentCameraBinding
import com.richarddewan.camerax.util.ANIMATION_FAST_MILLIS
import com.richarddewan.camerax.util.ANIMATION_SLOW_MILLIS
import com.richarddewan.camerax.util.EXTENSION_WHITELIST
import com.richarddewan.camerax.util.PermissionUtils
import com.richarddewan.camerax.util.PermissionUtils.checkIfAllPermissionGranted
import com.richarddewan.camerax.util.PermissionUtils.isLocationEnabled
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt


class CameraFragment : Fragment() {

    companion object {
        private const val TAG = "CameraFragment"
        private const val RATIO_4_3_VALUE = 4.0 / 3.0
        private const val RATIO_16_9_VALUE = 16.0 / 9.0
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val TIME_STAMP_FORMAT = "dd/MM/yyyy HH:mm:ss"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private const val REQUEST_CHECK_SETTINGS = 11
        private const val INTERVAL = 1000 * 10
        private const val FASTEST_INTERVAL = 1000 * 1
        private const val DISPLACEMENT = 10f
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }

    private lateinit var binding: FragmentCameraBinding

    ///Blocking camera operations are performed using this executor
    private lateinit var mCameraExecutor: ExecutorService
    private lateinit var viewFinder: PreviewView
    private lateinit var container: ConstraintLayout
    private var preview: Preview? = null
    private lateinit var mImageCapture: ImageCapture

    private lateinit var outputDirectory: File
    private lateinit var mCameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    lateinit var mProcessCameraProvider: ProcessCameraProvider
    private lateinit var mCameraSelector: CameraSelector

    private var displayId: Int = -1

    //private var lensFacing: Int = CameraSelector.LENS_FACING_BACK
    private var mFlashMode = ImageCapture.FLASH_MODE_OFF

    private lateinit var mImageCaptureBuilder: ImageCapture.Builder
    private lateinit var mBokehImage: BokehImageCaptureExtender
    private lateinit var mNightImage: NightImageCaptureExtender
    private lateinit var mHdrImage: HdrImageCaptureExtender
    private lateinit var mBeautyImage: BeautyImageCaptureExtender
    private var isBokehImage = false
    private var isNightImage = false
    private var isHdrImage = false
    private var isBeautyImage = false

    private lateinit var mPaint: Paint
    private lateinit var simpleDateFormat: SimpleDateFormat

    private lateinit var mLocationRequest: LocationRequest
    private lateinit var mSettingsClient: SettingsClient
    private lateinit var mFusedLocationClient: FusedLocationProviderClient
    private lateinit var mLocationCallback: LocationCallback
    private var mLatitude: Double = 0.0
    private var mLongitude: Double = 0.0
    private var mLocationTime: Long = 0L

    private lateinit var viewModel: CameraViewModel
    private val displayManager by lazy {
        requireContext().getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_camera, container, false)
        //Initialize the ViewModel
        viewModel = ViewModelProvider(this).get(CameraViewModel::class.java)
        //observer live data
        observers()

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize our background executor
        mCameraExecutor = Executors.newSingleThreadExecutor()
        // Every time the orientation of device changes, update rotation for use cases
        displayManager.registerDisplayListener(displayListener, null)

        viewFinder = binding.cameraPreview
        container = binding.cameraMainLayout
        outputDirectory = getOutputDirectory()

        mPaint = Paint().apply {
            flags = Paint.ANTI_ALIAS_FLAG and Paint.FILTER_BITMAP_FLAG
            textSize = 48f
            color = ContextCompat.getColor(requireContext(), R.color.material_white)
            style = Paint.Style.FILL
        }

        simpleDateFormat = SimpleDateFormat(TIME_STAMP_FORMAT, Locale.US)

        //location request
        setupLocationRequest()

    }

    private fun observers() {
        viewModel.isLoading.observe(viewLifecycleOwner, {
            binding.pbLoading.visibility = if (it) View.VISIBLE else View.GONE
        })

        viewModel.lensFacing.observe(viewLifecycleOwner, {
            Log.d(TAG, " LensFacing: $it")
        })
    }

    private fun setUpCamera() {
        Log.d(TAG, "setUpCamera")
        // Create a Builder same as in normal workflow.
        mImageCaptureBuilder = ImageCapture.Builder()

        /*
       Create an instance of the ProcessCameraProvider.
       This is used to bind the lifecycle of cameras to the lifecycle owner.
       This eliminates the task of opening and closing the camera since CameraX is lifecycle-aware.
        */
        mCameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        /*
         Create an Extender object which can be used to apply extension
         */

        //portrait mode
        mBokehImage = BokehImageCaptureExtender.create(mImageCaptureBuilder)

        //hdr mode
        mHdrImage = HdrImageCaptureExtender.create(mImageCaptureBuilder)

        //night mode
        mNightImage = NightImageCaptureExtender.create(mImageCaptureBuilder)

        //beauty mode
        mBeautyImage = BeautyImageCaptureExtender.create(mImageCaptureBuilder)

        /*
        Add a listener to the cameraProviderFuture.
        Add a Runnable as one argument.
        We will fill it in later.
         Add ContextCompat.getMainExecutor() as the second argument.
          This returns an Executor that runs on the main thread.
         */
        mCameraProviderFuture.addListener(Runnable {
            /*
            In the Runnable, add a ProcessCameraProvider.
            This is used to bind the lifecycle of your camera to the LifecycleOwner within the application's process.
            Used to bind the lifecycle of cameras to the lifecycle owner
             */
            mProcessCameraProvider = mCameraProviderFuture.get()

            // Select check for available camera if no camera found exit the fragment
            when {
                hasBackCamera() -> {
                    Log.d(TAG, "Front camera is available")
                }
                hasFrontCamera() -> {
                    Log.d(TAG, "Back camera is available")
                }
                else -> {
                    Log.e(TAG, "Back and front camera are unavailable")
                    requireActivity().supportFragmentManager.popBackStackImmediate()
                }
            }

            // Enable or disable switching between cameras
            updateCameraSwitchButton()

            //flash mode
            updateFlashMode()

            // Build and bind the camera use cases
            bindCameraUseCases()


        }, ContextCompat.getMainExecutor(requireContext()))

    }

    /**
     * Declare and bind preview, capture use cases
     * */
    private fun bindCameraUseCases() {
        Log.d(TAG, "bindCameraUseCases")

        // Get screen metrics used to setup camera for full screen resolution
        val metrics = DisplayMetrics().also { viewFinder.display.getRealMetrics(it) }
        Log.d(TAG, "Screen metrics: ${metrics.widthPixels} x ${metrics.heightPixels}")

        val screenAspectRatio = aspectRatio(metrics.widthPixels, metrics.heightPixels)
        Log.d(TAG, "Preview aspect ratio: $screenAspectRatio")

        //get rotation
        val rotation = viewFinder.display.rotation

        //Create a CameraSelector object and select DEFAULT_BACK_CAMERA.
        mCameraSelector = CameraSelector.Builder()
            .requireLensFacing(viewModel.lensFacing.value!!).build()

        // Preview
        /*
        Initialize your Preview object, call build on it,
        get a surface provider from viewfinder, and then set it on the preview.
         */
        preview = Preview.Builder()
            .setTargetAspectRatio(screenAspectRatio)
            .setTargetRotation(rotation)
            .build().also {
                it.setSurfaceProvider(viewFinder.surfaceProvider)
            }

        //setup image capture
        /**/

        if (isBeautyImage or isBokehImage or isHdrImage or isNightImage) {

            mImageCapture = mImageCaptureBuilder.apply {
                setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    /*
                    We request aspect ratio but no resolution to match preview config, but letting
                    CameraX optimize for whatever specific resolution best fits our use cases
                    */
                    .setTargetAspectRatio(screenAspectRatio)
                    /*
                    Set initial target rotation, we will have to call this again if rotation changes
                    during the lifecycle of this use case
                    */
                    .setTargetRotation(rotation)
                setFlashMode(mFlashMode)
            }.build()
        } else {

            mImageCapture = ImageCapture.Builder().apply {
                setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    /*
                    We request aspect ratio but no resolution to match preview config, but letting
                    CameraX optimize for whatever specific resolution best fits our use cases
                     */
                    .setTargetAspectRatio(screenAspectRatio)
                    /*
                     Set initial target rotation, we will have to call this again if rotation changes
                     during the lifecycle of this use case
                     */
                    .setTargetRotation(rotation)
                setFlashMode(mFlashMode)
            }.build()
        }

        /*
        Create a try block. Inside that block, make sure nothing is bound to your cameraProvider,
        and then bind your cameraSelector and preview object to the cameraProvider.
         */
        try {
            // Unbind use cases before rebinding
            mProcessCameraProvider.unbindAll()

            // Bind use cases to camera
            mProcessCameraProvider.bindToLifecycle(
                viewLifecycleOwner, mCameraSelector, preview, mImageCapture
            )

        }
        //There are a few ways this code could fail, like if the app is no longer in focus. Wrap this code in a catch block to log if there's a failure.
        catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    //image capture use case
    private fun takePhoto() {
        /*
        First, get a reference to the ImageCapture use case. If the use case is null,
        exit out of the function.
        This will be null If you tap the photo button before image capture is set up.
        Without the return statement, the app would crash if it was null.
         */
        if (!this::mImageCapture.isInitialized) {
            return
        } else {
            // create a file to hold the image. Add in a time stamp so the file name will be unique.
            val photoFile = File(
                outputDirectory, SimpleDateFormat(FILENAME_FORMAT, Locale.US)
                    .format(System.currentTimeMillis()) + ".jpg"
            )

            /*
            Create an OutputFileOptions object.
            This object is where you can specify things about how you want your output to be.
            You want the output saved in the file we just created, so add your photoFile.
             */
            val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

            // Set up image capture listener, which is triggered after photo has been taken
            mImageCapture.takePicture(
                outputOptions, mCameraExecutor, object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {

                        val savedUri = outputFileResults.savedUri ?: Uri.fromFile(photoFile)
                        Log.d(TAG, "Photo capture succeeded: $savedUri")

                        val exif = Exif.createFromFile(photoFile)
                        val rotation = exif.rotation

                        drawOnBitmap(photoFile, rotation)

                        // We can only change the foreground Drawable using API level 23+ API
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            // Update the gallery thumbnail with latest picture taken
                            setGalleryThumbnail(savedUri)
                        }

                        // Implicit broadcasts will be ignored for devices running API level >= 24
                        // so if you only target API level 24+ you can remove this statement
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                            requireActivity().sendBroadcast(
                                Intent(Camera.ACTION_NEW_PICTURE, savedUri)
                            )
                        }

                        // If the folder selected is an external media directory,this is unnecessary
                        // but otherwise other apps will not be able to access our
                        // images unless we scan them using [MediaScannerConnection]
                        val mimeType = MimeTypeMap.getSingleton()
                            .getMimeTypeFromExtension(savedUri.toFile().extension)
                        MediaScannerConnection.scanFile(
                            requireContext(),
                            arrayOf(savedUri.toFile().absolutePath),
                            arrayOf(mimeType)
                        ) { _, uri ->
                            Log.d(TAG, "Image capture scanned into media store: $uri")
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Log.e(TAG, "Photo capture failed: ${exception.message}", exception)
                    }

                })

            // We can only change the foreground Drawable using API level 23+ API
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Display flash animation to indicate that photo was captured
                container.postDelayed({
                    container.foreground = ColorDrawable(Color.WHITE)
                    container.postDelayed(
                        { container.foreground = null }, ANIMATION_FAST_MILLIS
                    )
                }, ANIMATION_SLOW_MILLIS)
            }
        }


    }

    /* Method used to re-draw the camera UI controls,
     * called every time configuration changes.
    */
    private fun updateCameraUi() {

        binding.imageCapture.setOnClickListener {
            takePhoto()
        }

        binding.photoView.setOnClickListener {
            // Only navigate when the gallery has photos
            if (true == outputDirectory.listFiles()?.isNotEmpty()) {
                val fragment = GalleryFragment.newInstance(outputDirectory, null)
                requireActivity().supportFragmentManager.beginTransaction()
                    .add(android.R.id.content, fragment)
                    .addToBackStack("MAIN")
                    .commit()
            }
        }

        binding.portraitView.setOnClickListener {
            enableBokehImageCaptureExtender()
        }

        binding.hdriView.setOnClickListener {
            enableHdrImageCaptureExtender()
        }

        binding.nightView.setOnClickListener {
            enableNightImageCaptureExtender()
        }

        binding.beautyView.setOnClickListener {
            enableBeautyImageCaptureExtender()
        }

        // In the background, load latest photo taken (if any) for gallery thumbnail
        lifecycleScope.launch(Dispatchers.IO) {
            outputDirectory.listFiles { file ->
                EXTENSION_WHITELIST.contains(file.extension.toUpperCase(Locale.ROOT))
            }?.maxOrNull()?.let {
                setGalleryThumbnail(Uri.fromFile(it))
            }
        }
    }

    /**
     *  [androidx.camera.core.ImageAnalysisConfig] requires enum value of
     *  [androidx.camera.core.AspectRatio]. Currently it has values of 4:3 & 16:9.
     *
     *  Detecting the most suitable ratio for dimensions provided in @params by counting absolute
     *  of preview ratio to one of the provided values.
     *
     *  @param width - preview width
     *  @param height - preview height
     *  @return suitable aspect ratio
     */
    private fun aspectRatio(width: Int, height: Int): Int {
        val previewRatio = max(width, height).toDouble() / min(width, height).toDouble()

        if (abs(previewRatio - RATIO_4_3_VALUE) <= abs(previewRatio - RATIO_16_9_VALUE)) {
            return AspectRatio.RATIO_4_3
        }

        return AspectRatio.RATIO_16_9
    }

    /*
    set image thumbnail
     */
    private fun setGalleryThumbnail(savedUri: Uri?) {
        val thumbnail = binding.photoView

        // Run the operations in the view's thread
        thumbnail.post {
            // Load thumbnail into circular button using Glide
            Glide.with(thumbnail)
                .load(savedUri)
                .apply(RequestOptions.circleCropTransform())
                .into(thumbnail)

        }

    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            requireActivity(),
            REQUIRED_PERMISSIONS,
            REQUEST_CODE_PERMISSIONS
        )
        /*
        for (i in REQUIRED_PERMISSIONS) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, i)) {
                openSystemSetting()
            }
            else {
                ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
            }
        }*/


    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (checkIfAllPermissionGranted(requireContext(), REQUIRED_PERMISSIONS)) {
                // Wait for the views to be properly laid out
                viewFinder.post {
                    // Keep track of the display in which this view is attached
                    displayId = viewFinder.display.displayId

                    // Build UI controls
                    updateCameraUi()

                    // Set up the camera and its use cases
                    setUpCamera()
                }
            } else {
                try {
                    Snackbar.make(
                        binding.cameraMainLayout,
                        getString(R.string.camera_permission_denied),
                        Snackbar.LENGTH_INDEFINITE
                    ).apply {
                        setBackgroundTint(
                            ContextCompat.getColor(context, R.color.material_red)
                        )
                        setTextColor(
                            ContextCompat.getColor(context, R.color.material_white)
                        )
                        setActionTextColor(
                            ContextCompat.getColor(context, R.color.material_white)
                        )
                        setAction(getString(R.string.dialog_close)) { _ ->
                            requestPermissions()
                        }
                    }.show()
                } catch (e: Exception) {
                    Log.e(TAG, e.toString())
                }

            }
        }
    }

    private fun openSystemSetting() {
        MaterialDialog(requireContext()).show {
            cancelable(false)
            cornerRadius(16F)
            icon(R.drawable.ic_action_unsuccess)
            title(R.string.dialog_camera_permission_title)
            message(R.string.dialog_camera_permission_msg)
            positiveButton(R.string.dialog_ok) {
                it.dismiss()
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", requireActivity().packageName, null)
                })
            }
        }

    }

    private fun getOutputDirectory(): File {
        val mediaDir = requireActivity().externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply {
                mkdir()
            }
        }
        return if (mediaDir != null && mediaDir.exists()) mediaDir else requireActivity().filesDir
    }

    private fun updateFlashMode() {
        binding.flashView.setOnClickListener {
            if (mFlashMode == ImageCapture.FLASH_MODE_ON) {
                mFlashMode = ImageCapture.FLASH_MODE_OFF
                binding.flashView.setImageDrawable(
                    ContextCompat.getDrawable(
                        requireContext(),
                        R.drawable.ic_action_flash_off
                    )
                )
                mImageCapture.flashMode = mFlashMode
            } else {
                mFlashMode = ImageCapture.FLASH_MODE_ON
                binding.flashView.setImageDrawable(
                    ContextCompat.getDrawable(
                        requireContext(),
                        R.drawable.ic_action_flash_on
                    )
                )
                mImageCapture.flashMode = mFlashMode
            }
        }
    }

    private fun updateCameraSwitchButton() {
        val cameraSwitch = binding.switchCameraView

        try {
            cameraSwitch.isEnabled = hasBackCamera() && hasFrontCamera()
        } catch (e: CameraInfoUnavailableException) {
            cameraSwitch.isEnabled = false
        }

        cameraSwitch.setOnClickListener {
            viewModel.lensFacing.value =
                if (CameraSelector.LENS_FACING_FRONT == viewModel.lensFacing.value) {
                    CameraSelector.LENS_FACING_BACK
                } else {
                    CameraSelector.LENS_FACING_FRONT
                }
            // Re-bind use cases to update selected camera
            bindCameraUseCases()
        }
    }

    /** Returns true if the device has an available back camera. False otherwise */
    private fun hasBackCamera(): Boolean {
        return mProcessCameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
    }

    /** Returns true if the device has an available front camera. False otherwise */
    private fun hasFrontCamera(): Boolean {
        return mProcessCameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
    }

    private fun enableBokehImageCaptureExtender() {
        isBokehImage = if (mBokehImage.isExtensionAvailable(mCameraSelector)) {
            mBokehImage.enableExtension(mCameraSelector)
            true
        } else {
            false
        }
        // Re-bind use cases to update selected camera
        bindCameraUseCases()
    }

    private fun enableHdrImageCaptureExtender() {
        isHdrImage = if (mHdrImage.isExtensionAvailable(mCameraSelector)) {
            mHdrImage.enableExtension(mCameraSelector)
            true
        } else {
            false
        }
        // Re-bind use cases to update selected camera
        bindCameraUseCases()
    }

    private fun enableNightImageCaptureExtender() {

        isNightImage = if (mNightImage.isExtensionAvailable(mCameraSelector)) {
            mNightImage.enableExtension(mCameraSelector)
            true
        } else {
            false
        }
        // Re-bind use cases to update selected camera
        bindCameraUseCases()
    }

    private fun enableBeautyImageCaptureExtender() {

        isBeautyImage = if (mBeautyImage.isExtensionAvailable(mCameraSelector)) {
            mBeautyImage.enableExtension(mCameraSelector)
            true
        } else {
            false
        }
        // Re-bind use cases to update selected camera
        bindCameraUseCases()
    }


    /**
     * Inflate camera controls and update the UI manually upon config changes to avoid removing
     * and re-adding the view finder from the view hierarchy; this provides a seamless rotation
     * transition on devices that support it.
     *
     * NOTE: The flag is supported starting in Android 8 but there still is a small flash on the
     * screen for devices that run Android 9 or below.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.d(TAG, "onConfigurationChanged")

        // Redraw the camera UI controls
        updateCameraUi()

        // Enable or disable switching between cameras
        updateCameraSwitchButton()
    }


    /**
     * We need a display listener for orientation changes that do not trigger a configuration
     * change, for example if we choose to override config change in manifest or for 180-degree
     * orientation changes.
     */
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit

        override fun onDisplayRemoved(displayId: Int) = Unit

        override fun onDisplayChanged(displayId: Int) {
            if (displayId == this@CameraFragment.displayId) {
                Log.d(TAG, "Rotation changed: ${requireView().display.rotation}")
                mImageCapture.targetRotation = requireView().display.rotation
            }
        }

    }

    /*
    draw time stamp on photo
     */
    private fun drawOnBitmap(file: File, degree: Int) {
        //create a bitmap from file
        val bitmapFromFile = BitmapFactory.decodeFile(file.path)

        //create a scale down image of 1920 x 1080
        val bitmap: Bitmap = when  {

            bitmapFromFile.width > bitmapFromFile.height ->
                Bitmap.createScaledBitmap(bitmapFromFile, 1920, 1080, true)

            bitmapFromFile.height > bitmapFromFile.width ->
                Bitmap.createScaledBitmap(bitmapFromFile, 1080, 1920, true)

            else ->
                Bitmap.createScaledBitmap(bitmapFromFile, 1920, 1080, true)
        }

        //rotate bitmap if required
        val rotateBitmap = rotateImage(bitmap, degree)

        //create a mutable bitmap
        val mutableBitmap = rotateBitmap.copy(Bitmap.Config.ARGB_8888, true)

        //pass mutable bitmap to canvas for drawing
        val canvas = Canvas(mutableBitmap)
        //print date time from location
        canvas.drawText(
            simpleDateFormat.format(mLocationTime),
            10f,
            mutableBitmap.height - 10f,
            mPaint
        )
        //print latitude an longitude
        canvas.drawText("$mLatitude - $mLongitude", 10f, mutableBitmap.height - 70f, mPaint)

        FileOutputStream(file).also {
            mutableBitmap.compress(Bitmap.CompressFormat.JPEG, 65, it)
            it.flush()
            it.close()
        }

    }


    /*
    fun to change the bitmap orientation and return bitmap
     */
    private fun rotateImage(bitmap: Bitmap, degree: Int): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degree.toFloat())

        return Bitmap.createBitmap(
            bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
        )
    }

    private fun enableLocationProvider() {
        viewModel.isLoading.value = true

        mSettingsClient = LocationServices.getSettingsClient(requireContext())
        val task: Task<LocationSettingsResponse> =
            mSettingsClient.checkLocationSettings(
                LocationSettingsRequest.Builder()
                    .addLocationRequest(mLocationRequest)
                    .build()
            )
        task.addOnSuccessListener {
            // All location settings are satisfied. The client can initialize
            viewModel.isLoading.value = false

            createLocationRequest()
        }
        task.addOnFailureListener { exception ->
            viewModel.isLoading.value = false

            if (exception is ResolvableApiException) {
                // Location settings are not satisfied, but this can be fixed
                // by showing the user a dialog.
                try {
                    // Show the dialog by calling startResolutionForResult(),
                    // and check the result in onActivityResult().
                    exception.startResolutionForResult(
                        requireActivity(),
                        REQUEST_CHECK_SETTINGS
                    )
                } catch (sendEx: IntentSender.SendIntentException) {
                    // Ignore the error.
                    Log.d(TAG, sendEx.message.toString())
                }
            }
        }
    }

    /*
    setup location request
     */
    private fun setupLocationRequest() {
        mLocationRequest = LocationRequest.create().apply {
            interval = INTERVAL.toLong()
            fastestInterval = FASTEST_INTERVAL.toLong()
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            smallestDisplacement = DISPLACEMENT
        }
    }

    @SuppressLint("MissingPermission")
    private fun createLocationRequest() {
        Log.d(TAG, "createLocationRequest")
        viewModel.isLoading.value = true
        //location callback
        locationUpdateCallback()
        //create an instance of the Fused Location Provider Client
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())

        mFusedLocationClient.requestLocationUpdates(
            mLocationRequest,
            mLocationCallback,
            Looper.getMainLooper()
        )
    }

    /*
    setup location callback
    location update callback is required if you want continues location update
     */
    private fun locationUpdateCallback() {
        mLocationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult?) {
                super.onLocationResult(locationResult)
                locationResult ?: return

                for (location in locationResult.locations) {
                    Log.d(TAG, location.toString())
                    mLatitude = location.latitude
                    mLongitude = location.latitude
                    mLocationTime = location.time
                    viewModel.isLoading.postValue(false)
                }
            }
        }
    }

    /*
    Stop location updates
    Consider whether you want to stop the location updates when the activity is no longer in focus,
    such as when the user switches to another app or to a different activity in the same app.
    This can be handy to reduce power consumption,
    provided the app doesn't need to collect information even when it's running in the background.
     */
    private fun stopLocationUpdate() {
        Log.d(TAG, "stopLocationUpdate")
        if (this::mFusedLocationClient.isInitialized) {
            if (this::mLocationCallback.isInitialized) {
                mFusedLocationClient.removeLocationUpdates(mLocationCallback)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        when {
            checkIfAllPermissionGranted(requireContext(), REQUIRED_PERMISSIONS) -> {
                // Wait for the views to be properly laid out
                viewFinder.post {
                    // Keep track of the display in which this view is attached
                    displayId = viewFinder.display.displayId
                    // Build UI controls
                    updateCameraUi()
                    // Set up the camera and its use cases
                    setUpCamera()
                }

                if (!isLocationEnabled(requireContext())) {
                    enableLocationProvider()
                } else {
                    createLocationRequest()
                }
            }
            else -> {
                requestPermissions()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause")
        //stop location update
        stopLocationUpdate()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Unregister the listeners
        displayManager.unregisterDisplayListener(displayListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Shut down our background executor
        mCameraExecutor.shutdown()
    }


}