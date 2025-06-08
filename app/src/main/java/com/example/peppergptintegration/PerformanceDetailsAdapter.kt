package com.example.peppergptintegration

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.peppergptintegration.databinding.ItemPerformanceBinding
import java.text.SimpleDateFormat
import java.util.*

class PerformanceDetailsAdapter : ListAdapter<PerformanceDetails, PerformanceDetailsAdapter.ViewHolder>(
    DiffCallback()
) {
    class ViewHolder(val binding: ItemPerformanceBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPerformanceBinding.inflate(
            LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.binding.apply {
            categoryText.text = item.category
            overallScoreText.text = "${(item.overallScore * 100).toInt()}%"
            verbalAccuracyText.text = "${(item.verbalAccuracy * 100).toInt()}%"
            selectionAccuracyText.text = "${(item.selectionAccuracy * 100).toInt()}%"
            lastUpdatedText.text = formatDate(item.lastUpdated)
        }
    }

    private fun formatDate(dateString: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val outputFormat = SimpleDateFormat("MMM d", Locale.getDefault())
            val date = inputFormat.parse(dateString)
            outputFormat.format(date)
        } catch (e: Exception) {
            dateString
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<PerformanceDetails>() {
        override fun areItemsTheSame(oldItem: PerformanceDetails, newItem: PerformanceDetails) =
            oldItem.category == newItem.category

        override fun areContentsTheSame(oldItem: PerformanceDetails, newItem: PerformanceDetails) =
            oldItem == newItem
    }
}