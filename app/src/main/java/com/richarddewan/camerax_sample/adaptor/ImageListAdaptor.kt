package com.richarddewan.camerax_sample.adaptor

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.richarddewan.camerax_sample.R
import java.io.File


/*
created by Richard Dewan 30/10/2020
*/

class ImageListAdaptor(
    private val itemClickListener: (file: File) -> Unit
) : RecyclerView.Adapter<ImageListAdaptor.ImageViewHolder>() {

    private val diffUtil = object: DiffUtil.ItemCallback<File>(){
        override fun areItemsTheSame(oldItem: File, newItem: File): Boolean {
            return oldItem.name == newItem.name
        }

        override fun areContentsTheSame(oldItem: File, newItem: File): Boolean {
            return oldItem == newItem
        }
    }

    private val asyncListDiffer = AsyncListDiffer(this,diffUtil)

    fun setImageList(newList : MutableList<File>){
        asyncListDiffer.submitList(newList)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.image_list_view,parent,false)
        return ImageViewHolder(view,itemClickListener)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        holder.onBind(asyncListDiffer.currentList[position])
    }

    override fun getItemCount(): Int = asyncListDiffer.currentList.size

    class ImageViewHolder(
        itemView: View,
        private val itemClickListener: (file: File) ->Unit): RecyclerView.ViewHolder(itemView){

        private val imageView = itemView.findViewById<ImageView>(R.id.list_imageView)

        fun onBind(data: File){
            Glide.with(imageView.context)
                .load(data.path)
                .into(imageView)
            itemView.setOnClickListener {
                itemClickListener(data)
            }
        }
    }

}