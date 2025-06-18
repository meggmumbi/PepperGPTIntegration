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
import com.google.android.material.chip.Chip
import com.google.android.material.progressindicator.LinearProgressIndicator
import java.text.SimpleDateFormat
import java.util.*

class CategoriesAdapter(
    private var categories: List<Category>,
    private val onItemClick: (Category) -> Unit,
    private val onViewClick: (Category) -> Unit,
) : ListAdapter<Category, CategoriesAdapter.CategoryViewHolder>(CategoryDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category, parent, false)
        return CategoryViewHolder(view, onItemClick, onViewClick)
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
        private val descriptionTextView: TextView =
            itemView.findViewById(R.id.categoryDescriptionTextView)
        private val difficultyTextView: Chip =
            itemView.findViewById(R.id.categoryDifficultyTextView)
        private val viewButton: MaterialButton = itemView.findViewById(R.id.viewItemButton)

        private val itemCountTextView: TextView = itemView.findViewById(R.id.itemCountTextView)
        private val attemptsTextView: TextView = itemView.findViewById(R.id.attemptsTextView)
        private val performanceTextView: TextView = itemView.findViewById(R.id.performanceTextView)
        private val lastAttemptTextView: TextView = itemView.findViewById(R.id.lastAttemptTextView)
        private val progressIndicator: LinearProgressIndicator =
            itemView.findViewById(R.id.progressIndicator)


        fun bind(category: Category) {
            nameTextView.text = category.name
            descriptionTextView.text = category.description

            // Set difficulty with appropriate color
            difficultyTextView.text = category.difficultyLevel
            difficultyTextView.setChipBackgroundColorResource(
                when (category.difficultyLevel.lowercase()) {
                    "easy" -> R.color.difficulty_easy
                    "medium" -> R.color.difficulty_medium
                    "advanced" -> R.color.difficulty_advanced
                    else -> R.color.difficulty_default
                }
            )

            // Set stats with null checks
            itemCountTextView.text = "${category.itemCount ?: 0} items"
            attemptsTextView.text = "${category.totalAttempts ?: 0} attempts"
            performanceTextView.text = "${category.latestPerformance?.toInt() ?: 0}%"

            // Set progress with null check
            progressIndicator.progress = category.latestPerformance?.toInt() ?: 0

            // Format last attempt date
            category.lastAttemptDate?.let {
                lastAttemptTextView.text = "Last attempt: ${formatDate(it)}"
                lastAttemptTextView.visibility = View.VISIBLE
            } ?: run {
                lastAttemptTextView.visibility = View.GONE
            }

            itemView.setOnClickListener { onItemClick(category) }
            viewButton.setOnClickListener { onViewClick(category) }
        }

        private fun formatDate(dateString: String): String {
            return try {
                val date = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS", Locale.getDefault())
                    .parse(dateString)
                SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(date!!)
            } catch (e: Exception) {
                // Fallback to showing the raw string if parsing fails
                dateString
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
}