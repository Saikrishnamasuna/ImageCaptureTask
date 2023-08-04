package com.example.imagecapturetask.ui.adapter

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.imagecapturetask.databinding.ImageListBinding
import com.example.imagecapturetask.ui.model.ImageData

class ImageAdapter(
    private val context: Context,
    var list: ArrayList<ImageData>,
    private val itemClickListener: ItemClickListener
) : RecyclerView.Adapter<ImageAdapter.ViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageAdapter.ViewHolder {
        val v = ImageListBinding.inflate(LayoutInflater.from(parent.context), parent, false)

        return ViewHolder(v)
    }

    override fun onBindViewHolder(holder: ImageAdapter.ViewHolder, position: Int) {
        val imageData = list.get(position)

        holder.binding.name.text = imageData.imageName

        Glide.with(context).load(Uri.parse(imageData.bitmap)).override(1600, 1600)
            .into(holder.binding.image)

    }

    override fun getItemCount(): Int {
        return list.size
    }

    inner class ViewHolder(var binding: ImageListBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.card.setOnClickListener {
                val path = list.get(adapterPosition).bitmap

                if (position != RecyclerView.NO_POSITION) {
                    itemClickListener.onItemClick(path)
                }
            }
        }
    }

    interface ItemClickListener {
        fun onItemClick(path: String)
    }
}