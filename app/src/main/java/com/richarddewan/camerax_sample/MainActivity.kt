package com.richarddewan.camerax_sample

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.richarddewan.camerax.ui.GalleryFragment
import com.richarddewan.camerax.ui.MainActivityHolder
import com.richarddewan.camerax.util.DisplayMetricsHelper
import com.richarddewan.camerax.util.EXTENSION_WHITELIST
import com.richarddewan.camerax.util.FLAGS_FULLSCREEN
import com.richarddewan.camerax.util.IMMERSIVE_FLAG_TIMEOUT
import com.richarddewan.camerax_sample.adaptor.ImageListAdaptor
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.*

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "MainActivity"
    }
    private lateinit var outputDirectory: File
    private lateinit var mediaList: MutableList<File>
    private lateinit var mRecyclerView: RecyclerView
    private lateinit var mAdaptor: ImageListAdaptor
    private var mSpanCount = 1
    private lateinit var container: ConstraintLayout

    private val mLinearLayoutManager: LinearLayoutManager by lazy {
        LinearLayoutManager(this)
    }
    private val mGridLayoutManager: GridLayoutManager by lazy {
        GridLayoutManager(this,mSpanCount, GridLayoutManager.VERTICAL,false)
    }

    private val mStaggeredGridLayoutManager: StaggeredGridLayoutManager by lazy {
        StaggeredGridLayoutManager(mSpanCount,GridLayoutManager.VERTICAL)
    }

    private val itemClickListener: (file: File) ->Unit = {
        val fragment = GalleryFragment.newInstance(outputDirectory, it)
        supportFragmentManager.beginTransaction()
            .add(android.R.id.content,fragment)
            .addToBackStack(TAG)
            .commit()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        container = findViewById(R.id.activity_mainLayout)

        val displayHelper = DisplayMetricsHelper(this)
        mRecyclerView = findViewById(R.id.rv_image_gallery)
        mAdaptor = ImageListAdaptor(itemClickListener)

        outputDirectory = getOutputDirectory()

        mRecyclerView.apply {
            layoutManager = mStaggeredGridLayoutManager
            adapter = mAdaptor
            hasFixedSize()
        }

        when {
            displayHelper.isPhone() -> {
                mSpanCount = 3
                mStaggeredGridLayoutManager.spanCount = mSpanCount
                mStaggeredGridLayoutManager.gapStrategy =
                    StaggeredGridLayoutManager.GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS

            }
            displayHelper.is7InchTablet() -> {
                mSpanCount = 4
                mStaggeredGridLayoutManager.spanCount = mSpanCount
                mStaggeredGridLayoutManager.gapStrategy =
                    StaggeredGridLayoutManager.GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS

            }
            displayHelper.is10InchTablet() -> {
                mSpanCount = 5
                mStaggeredGridLayoutManager.spanCount = mSpanCount
                mStaggeredGridLayoutManager.gapStrategy =
                    StaggeredGridLayoutManager.GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS
            }
        }

        btnCamera.setOnClickListener {
            val intent = Intent(applicationContext,MainActivityHolder::class.java)
            startActivity(intent)
        }

    }

    /*
    File[]
    the absolute paths to application-specific directories.
    Some individual paths may be null if that shared storage is not currently available.
     */
    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {file->
            File(file,resources.getString(R.string.app_name))
        }
        return if (mediaDir != null && mediaDir.exists()) mediaDir else filesDir
    }

    /*
    get the list of image file from the application-specific directories.
     */
    private fun getFileList(){
        lifecycleScope.launch(Dispatchers.Main){

            mediaList = outputDirectory.listFiles { filter->
                EXTENSION_WHITELIST.contains(filter.extension.toUpperCase(Locale.US))
            }?.sortedDescending()?.toMutableList() ?: mutableListOf()

            mAdaptor.setImageList(mediaList)

            Log.d(TAG,mediaList.size.toString())
        }
    }

    // Shows the system bars by removing all the flags
    // except for the ones that make the content appear under the system bars.
    private fun showSystemUI() {
        container.postDelayed({
            container.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    )
        }, IMMERSIVE_FLAG_TIMEOUT)

    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG,"onResume")
        //showSystemUI()
        getFileList()

    }

}