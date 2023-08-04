package com.example.imagecapturetask.ui.view

import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.example.imagecapturetask.R
import com.example.imagecapturetask.databinding.ActivityImageViewerBinding

class ImageViewerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityImageViewerBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = DataBindingUtil.setContentView(this, R.layout.activity_image_viewer)

        val imagePath = intent.getStringExtra("imagePath")

        if (!imagePath.isNullOrEmpty()) {
            // Load image from a file path
            binding.imageView.setImageURI(Uri.parse(imagePath))
        }
    }
}