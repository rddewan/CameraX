package com.richarddewan.camerax.ui

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.FrameLayout
import androidx.databinding.DataBindingUtil
import com.richarddewan.camerax.R
import com.richarddewan.camerax.databinding.ActivityCameraBinding
import com.richarddewan.camerax.util.*


class MainActivityHolder : AppCompatActivity() {

    private lateinit var binding: ActivityCameraBinding
    private lateinit var container: FrameLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //setContentView(R.layout.activity_camera)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_camera)
        container = binding.cameraMainLayout
    }

    override fun onResume() {
        super.onResume()
        container.postDelayed({
            container.systemUiVisibility = FLAGS_FULLSCREEN
        }, IMMERSIVE_FLAG_TIMEOUT)
    }
}