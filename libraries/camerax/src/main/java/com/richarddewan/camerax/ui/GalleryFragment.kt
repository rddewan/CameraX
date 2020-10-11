package com.richarddewan.camerax.ui

import android.content.Intent
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.FrameLayout
import android.widget.ImageButton
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.FileProvider
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import androidx.viewpager2.widget.ViewPager2
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.richarddewan.camerax.BuildConfig
import com.richarddewan.camerax.R
import com.richarddewan.camerax.databinding.FragmentGalleryBinding
import com.richarddewan.camerax.ui.adaptor.GalleryAdaptor
import com.richarddewan.camerax.util.EXTENSION_WHITELIST
import com.richarddewan.camerax.util.FLAGS_FULLSCREEN
import com.richarddewan.camerax.util.IMMERSIVE_FLAG_TIMEOUT
import com.richarddewan.camerax.util.showImmersive
import kotlinx.android.synthetic.main.fragment_gallery.*
import kotlinx.android.synthetic.main.gallery_image_item.view.*
import java.io.File
import java.util.*


class GalleryFragment : Fragment() {

    private lateinit var galleryAdaptor: GalleryAdaptor
    private lateinit var mediaList: MutableList<File>
    private lateinit var viewPager2: ViewPager2
    private lateinit var fileDir: File
    private lateinit var binding: FragmentGalleryBinding
    private lateinit var container: ConstraintLayout

    private  val deleteImage: (position: Int)-> Unit = { position->
        Log.d("GalleryFragment",position.toString())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Mark this as a retain fragment, so the lifecycle does not get restarted on config change
        retainInstance = true

        galleryAdaptor = GalleryAdaptor()

        mediaList = fileDir.listFiles { file ->
            EXTENSION_WHITELIST.contains(file.extension.toUpperCase(Locale.ROOT))
        }?.sortedDescending()?.toMutableList() ?: mutableListOf()

        galleryAdaptor.setDate(mediaList)

    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_gallery, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        container = binding.mainLayout
        viewPager2 = binding.galeryViewPager
        viewPager2.adapter = galleryAdaptor

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
        binding.deleteButton.setOnClickListener {

            mediaList.getOrNull(viewPager2.currentItem)?.let { mediaFile ->

                AlertDialog.Builder(view.context, android.R.style.Theme_Material_Dialog)
                    .setTitle(getString(R.string.delete_title))
                    .setMessage(getString(R.string.delete_dialog))
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setPositiveButton(android.R.string.yes) { _, _ ->

                        // Delete current photo
                        mediaFile.delete()

                        // Send relevant broadcast to notify other apps of deletion
                        MediaScannerConnection.scanFile(
                            view.context, arrayOf(mediaFile.absolutePath), null, null)

                        // Notify our view pager
                        mediaList.removeAt(viewPager2.currentItem)
                        galleryAdaptor.notifyItemRemoved(viewPager2.currentItem)

                        // If all photos have been deleted, return to camera
                        if (mediaList.isEmpty()) {
                            requireActivity().supportFragmentManager.popBackStackImmediate()
                        }

                    }

                    .setNegativeButton(android.R.string.no, null)
                    .create().showImmersive()
            }
        }

        binding.shareButton.setOnClickListener {

            mediaList.getOrNull(viewPager2.currentItem)?.let {file ->
                Intent().also {
                    // Get URI from our FileProvider implementation
                    val uri = FileProvider.getUriForFile(
                        requireContext(),"${requireActivity().packageName}.provider",file)
                    it.putExtra(Intent.EXTRA_STREAM,uri)

                    // Infer media type from file extension
                    it.type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension)
                    // Set the appropriate intent extra, type, action and flags
                    it.action = Intent.ACTION_SEND
                    it.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    it.resolveActivity(requireActivity().packageManager)?.apply {
                        container.postDelayed({
                            container.systemUiVisibility = FLAGS_FULLSCREEN
                        }, IMMERSIVE_FLAG_TIMEOUT)
                        startActivity(Intent.createChooser(it,"share using"))

                    }
                }

            }

        }

    }

    companion object {

        @JvmStatic
        fun newInstance(param: File) =
            GalleryFragment().apply {
                arguments = Bundle().apply {
                    fileDir = param
                }

            }
    }
}