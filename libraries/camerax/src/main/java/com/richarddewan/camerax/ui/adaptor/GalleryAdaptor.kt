package com.richarddewan.camerax.ui.adaptor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.richarddewan.camerax.R
import java.io.File


/*
created by Richard Dewan 06/10/2020
*/

class GalleryAdaptor : RecyclerView.Adapter<GalleryAdaptor.ImageViewHolder>(){

    private val diffUtil = object : DiffUtil.ItemCallback<File>() {
        override fun areItemsTheSame(oldItem: File, newItem: File): Boolean {
            return oldItem.name == newItem.name
        }

        override fun areContentsTheSame(oldItem: File, newItem: File): Boolean {
            return oldItem == newItem
        }
    }

    private val asyncListDiffer = AsyncListDiffer(this,diffUtil)

    fun setDate(list: List<File>){
        asyncListDiffer.submitList(list)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.gallery_image_item,parent,false)

        return ImageViewHolder(view)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        holder.onBind(asyncListDiffer.currentList[position])
    }

    override fun getItemCount(): Int = asyncListDiffer.currentList.size

    class ImageViewHolder(itemView: View): RecyclerView.ViewHolder(itemView){
        private val imageView = itemView.findViewById<SubsamplingScaleImageView>(R.id.imageView)

        fun onBind(data: File){
            Glide.with(itemView)
                .asBitmap()
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .load(BitmapFactory.decodeFile(data.path))
                .into(object : CustomTarget<Bitmap>() {
                    override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                        imageView.setImage(ImageSource.cachedBitmap(resource))
                    }
                    override fun onLoadCleared(placeholder: Drawable?) {

                    }
                })

        }
    }

}