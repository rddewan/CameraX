package com.richarddewan.camerax.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.media.MediaScannerConnection
import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.MimeTypeMap
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.richarddewan.camerax.R
import com.richarddewan.camerax.databinding.FragmentGalleryBinding
import com.richarddewan.camerax.ui.adaptor.GalleryAdaptor
import com.richarddewan.camerax.util.EXTENSION_WHITELIST
import java.io.File
import java.util.*


class GalleryFragment : Fragment() {
    private val hideHandler = Handler()

    private lateinit var galleryAdaptor: GalleryAdaptor
    private lateinit var mediaList: MutableList<File>
    private lateinit var viewPager2: ViewPager2
    private lateinit var fileDir: File
    private var fileName: File? = null
    private var mPosition = 0

    private lateinit var binding: FragmentGalleryBinding

    @Suppress("InlinedApi")
    private val hidePartRunnable = Runnable {
        // Delayed removal of status and navigation bar

        // Note that some of these constants are new as of API 16 (Jelly Bean)
        // and API 19 (KitKat). It is safe to use them, as they are inlined
        // at compile-time and do nothing on earlier devices.
        val flags =
            View.SYSTEM_UI_FLAG_LOW_PROFILE or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        activity?.window?.decorView?.systemUiVisibility = flags
        //(activity as? AppCompatActivity)?.supportActionBar?.hide()
    }

    private var visible: Boolean = false
    private val hideRunnable = Runnable { hide() }

    /**
     * Touch listener to use for in-layout UI controls to delay hiding the
     * system UI. This is to prevent the jarring behavior of controls going away
     * while interacting with activity UI.
     */
    @SuppressLint("ClickableViewAccessibility")
    private val delayHideTouchListener = View.OnTouchListener { _, _ ->
        if (AUTO_HIDE) {
            delayedHide(AUTO_HIDE_DELAY_MILLIS)
        }
        false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //instantiate PhotoGalleryAdaptor
        galleryAdaptor = GalleryAdaptor()
        //get the list of file from file dit
        mediaList = fileDir.listFiles { file ->
            EXTENSION_WHITELIST.contains(
                file.extension.toUpperCase(
                    Locale.US
                )
            )
        }?.sortedDescending()
            ?.toMutableList() ?: mutableListOf()

        //get the current position by file as we want to move to specific position in viewpager
        fileName?.let {
            mPosition = mediaList.indexOf(fileName)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = DataBindingUtil.inflate(
            inflater, R.layout.fragment_gallery, container, false
        )

        return binding.root
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        visible = true
        viewPager2 = binding.galleryViewPager
        viewPager2.adapter = galleryAdaptor
        galleryAdaptor.setDate(mediaList)

        /*
        if the position is >0 set the current item on viewpager2
         */
        if (mPosition > 0) {
            viewPager2.post {
                viewPager2.setCurrentItem(mPosition, false)
            }
        }

        //Checking media files list
        if (mediaList.isEmpty()) {
            binding.shareButton.isEnabled = false
            binding.deleteButton.isEnabled = false
        }

        // Handle back button press
        binding.backButton.setOnClickListener {
            requireActivity().supportFragmentManager.popBackStackImmediate()
        }

        // Handle delete button press
        binding.deleteButton.setOnTouchListener(delayHideTouchListener)
        binding.deleteButton.setOnClickListener {

            mediaList.getOrNull(viewPager2.currentItem)?.let { mediaFile ->

                AlertDialog.Builder(view.context, R.style.CameraTheme_AlertDialogStyle)
                    .setTitle(getString(R.string.delete_title))
                    .setMessage(getString(R.string.delete_dialog))
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setPositiveButton(R.string.dialog_yes) { _, _ ->

                        // Delete current photo
                        mediaFile.delete()

                        // Send relevant broadcast to notify other apps of deletion
                        MediaScannerConnection.scanFile(
                            view.context, arrayOf(mediaFile.absolutePath), null, null
                        )

                        // Notify our view pager
                        mediaList.removeAt(viewPager2.currentItem)
                        galleryAdaptor.notifyItemRemoved(viewPager2.currentItem)

                        // If all photos have been deleted, return to camera
                        if (mediaList.isEmpty()) {
                            requireActivity().supportFragmentManager.popBackStackImmediate()
                        }

                    }
                    .setNegativeButton(android.R.string.no, null)
                    .create().show()
            }
        }


        binding.shareButton.setOnTouchListener(delayHideTouchListener)
        binding.shareButton.setOnClickListener {

            mediaList.getOrNull(viewPager2.currentItem)?.let { file ->
                Intent().also {
                    // Get URI from our FileProvider implementation
                    val uri = FileProvider.getUriForFile(
                        requireContext(), "${requireActivity().packageName}.provider", file
                    )
                    it.putExtra(Intent.EXTRA_STREAM, uri)

                    // Infer media type from file extension
                    it.type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension)
                    // Set the appropriate intent extra, type, action and flags
                    it.action = Intent.ACTION_SEND
                    it.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    it.resolveActivity(requireActivity().packageManager)?.apply {

                        startActivity(Intent.createChooser(it, "share using"))

                    }
                }

            }

        }


        /*dummyButton = view.findViewById(R.id.dummy_button)
        fullscreenContent = view.findViewById(R.id.fullscreen_content)
        fullscreenContentControls = view.findViewById(R.id.fullscreen_content_controls)
        // Set up the user interaction to manually show or hide the system UI.
        fullscreenContent?.setOnClickListener { toggle() }

        // Upon interacting with UI controls, delay any scheduled hide()
        // operations to prevent the jarring behavior of controls going away
        // while interacting with the UI.
        dummyButton?.setOnTouchListener(delayHideTouchListener)*/
    }

    override fun onResume() {
        super.onResume()
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        // Trigger the initial hide() shortly after the activity has been
        // created, to briefly hint to the user that UI controls
        // are available.
        delayedHide(100)
    }

    override fun onPause() {
        super.onPause()
        activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)

        // Clear the systemUiVisibility flag
        activity?.window?.decorView?.systemUiVisibility = 0
    }

    private fun hide() {
        // Hide UI first
        visible = false
        // Schedule a runnable to remove the status and navigation bar after a delay
        hideHandler.postDelayed(hidePartRunnable, UI_ANIMATION_DELAY.toLong())
    }



    /**
     * Schedules a call to hide() in [delayMillis], canceling any
     * previously scheduled calls.
     */
    private fun delayedHide(delayMillis: Int) {
        hideHandler.removeCallbacks(hideRunnable)
        hideHandler.postDelayed(hideRunnable, delayMillis.toLong())
    }

    companion object {
        /**
         * Whether or not the system UI should be auto-hidden after
         * [AUTO_HIDE_DELAY_MILLIS] milliseconds.
         */
        private const val AUTO_HIDE = true

        /**
         * If [AUTO_HIDE] is set, the number of milliseconds to wait after
         * user interaction before hiding the system UI.
         */
        private const val AUTO_HIDE_DELAY_MILLIS = 1000

        /**
         * Some older devices needs a small delay between UI widget updates
         * and a change of the status and navigation bar.
         */
        private const val UI_ANIMATION_DELAY = 300

        @JvmStatic
        fun newInstance(param: File, name: File?) =
            GalleryFragment().apply {
                arguments = Bundle().apply {
                    fileDir = param
                    name?.let {
                        fileName = it
                    }
                }
            }

    }
}