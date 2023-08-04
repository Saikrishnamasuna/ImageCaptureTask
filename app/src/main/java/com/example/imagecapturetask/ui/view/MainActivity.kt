package com.example.imagecapturetask.ui.view

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.imagecapturetask.R
import com.example.imagecapturetask.databinding.ActivityMainBinding
import com.example.imagecapturetask.ui.adapter.ImageAdapter
import com.example.imagecapturetask.ui.model.ImageData
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class MainActivity : AppCompatActivity(), ImageAdapter.ItemClickListener {
    lateinit var binding: ActivityMainBinding
    private lateinit var imageList: ArrayList<ImageData>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)


        // Set your icon here
        binding.addFab.setOnClickListener {
            // Handle floating button click
            // Example: Open a new activity
            val intent = Intent(this, CustomCameraActivity::class.java)
            startActivity(intent)
        }

    }

    private fun setDataToRecyclerView() {
        binding.recyclerview.layoutManager = LinearLayoutManager(this)
        binding.recyclerview.adapter = ImageAdapter(this, list = imageList, this)
    }

    override fun onStart() {
        super.onStart()
        // Fetching data from shared preferences by converting string to json using gson
        try {
            val sharedPreference = getSharedPreferences("PREFERENCE_NAME", Context.MODE_PRIVATE)
            val gsonobj = sharedPreference.getString("ImageList", "") ?: ""
            if (gsonobj.isNotEmpty()) {
                val gson = Gson()
                val type = object : TypeToken<List<ImageData?>?>() {}.type
                imageList = gson.fromJson(gsonobj, type)
              //  Toast.makeText(this, "Size of images" + imageList.size, Toast.LENGTH_SHORT).show()
                setDataToRecyclerView()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onItemClick(path: String) {
        val intent = Intent(this, ImageViewerActivity::class.java)
        intent.putExtra("imagePath", path)
        startActivity(intent)
    }
}



