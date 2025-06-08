package com.example.peppergptintegration

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton

class CategoriesAdapter(
    private var categories: List<Category>,
    private val onItemClick: (Category) -> Unit,
    private val onViewClick: (Category) -> Unit,
) : ListAdapter<Category, CategoriesAdapter.CategoryViewHolder>(CategoryDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category, parent, false)
        return CategoryViewHolder(view, onItemClick,onViewClick)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    fun updateCategories(categories: List<Category>) {
        submitList(categories.toMutableList())
    }

    fun addCategories(categories: List<Category>) {
        val currentList = currentList.toMutableList()
        currentList.addAll(categories)
        submitList(currentList)
    }

    class CategoryViewHolder(
        itemView: View,
        private val onItemClick: (Category) -> Unit,
        private val onViewClick: (Category) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val nameTextView: TextView = itemView.findViewById(R.id.categoryNameTextView)
        private val descriptionTextView: TextView = itemView.findViewById(R.id.categoryDescriptionTextView)
        private val difficultyTextView: TextView = itemView.findViewById(R.id.categoryDifficultyTextView)
        private val viewButton: MaterialButton = itemView.findViewById(R.id.viewItemButton)


        fun bind(category: Category) {
            nameTextView.text = category.name
            descriptionTextView.text = category.description
            difficultyTextView.text = "Difficulty: ${category.difficultyLevel}"

            itemView.setOnClickListener {
                onItemClick(category)
            }
            viewButton.setOnClickListener { onViewClick(category) }
        }
    }
}

class CategoryDiffCallback : DiffUtil.ItemCallback<Category>() {
    override fun areItemsTheSame(oldItem: Category, newItem: Category): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: Category, newItem: Category): Boolean {
        return oldItem == newItem
    }
}