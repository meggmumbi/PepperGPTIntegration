package com.example.peppergptintegration

import android.graphics.BitmapFactory
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class CategoryItemsAdapter(
    private var items: List<CategoryItem>,
    private val onViewClick: (CategoryItem) -> Unit,
    private val onDeleteClick: (CategoryItem) -> Unit
) : RecyclerView.Adapter<CategoryItemsAdapter.CategoryItemViewHolder>() {

    inner class CategoryItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameTextView: TextView = itemView.findViewById(R.id.itemNameTextView)
        private val difficultyTextView: TextView = itemView.findViewById(R.id.itemDifficultyTextView)
        private val imageView: ImageView = itemView.findViewById(R.id.itemImageView)
        private val viewButton: ImageButton = itemView.findViewById(R.id.viewItemButton)
        private val deleteButton: ImageButton = itemView.findViewById(R.id.deleteItemButton)

        fun bind(item: CategoryItem) {
            nameTextView.text = item.name
            difficultyTextView.text = "Difficulty: ${item.difficultyLevel.capitalize()}"

            // Load base64 image if available
            item.imageBase64?.let { base64String ->
                try {
                    val imageBytes = Base64.decode(base64String, Base64.DEFAULT)
                    val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                    imageView.setImageBitmap(bitmap)
                } catch (e: Exception) {
                    imageView.setImageResource(R.drawable.ic_broken_image)
                }
            } ?: run {
                imageView.setImageResource(R.drawable.ic_no_image)
            }

            viewButton.setOnClickListener { onViewClick(item) }
            deleteButton.setOnClickListener { onDeleteClick(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryItemViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category_item, parent, false)
        return CategoryItemViewHolder(view)
    }

    override fun onBindViewHolder(holder: CategoryItemViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    fun updateItems(newItems: List<CategoryItem>) {
        items = newItems
        notifyDataSetChanged()
    }
}