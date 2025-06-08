package com.example.peppergptintegration

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.peppergptintegration.databinding.ItemSessionHistoryBinding
import java.text.SimpleDateFormat
import java.util.*

class SessionHistoryAdapter(
    private val onItemClicked: (SessionHistoryItem) -> Unit
) : ListAdapter<SessionHistoryItem, SessionHistoryAdapter.ViewHolder>(DiffCallback()) {

    class ViewHolder(val binding: ItemSessionHistoryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSessionHistoryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.binding.apply {
            root.setOnClickListener { onItemClicked(item) }

            // Basic session info
            categoryText.text = item.category
            dateText.text = formatDate(item.date)

            // Duration with icon
            durationText.text = item.durationMinutes?.let {
                "${it.toInt()} min"
            } ?: "N/A"
            durationIcon.setImageResource(R.drawable.ic_time)

            // Score with visual indicator
            item.score?.let { score ->
                scoreText.text = "${(score * 100).toInt()}%"
                scoreIndicator.backgroundTintList = ColorStateList.valueOf(
                    when {
                        score > 0.75 -> ContextCompat.getColor(root.context, R.color.success)
                        score > 0.5 -> ContextCompat.getColor(root.context, R.color.warning)
                        else -> ContextCompat.getColor(root.context, R.color.error)
                    }
                )
                scoreText.visibility = View.VISIBLE
                scoreIndicator.visibility = View.VISIBLE
            } ?: run {
                scoreText.text = "N/A"
                scoreText.visibility = View.VISIBLE
                scoreIndicator.visibility = View.INVISIBLE
            }

            // Feedback indicator
            feedbackIndicator.visibility = if (item.feedback != null) View.VISIBLE else View.GONE
            feedbackIndicator.setImageResource(
                if (item.feedback != null) R.drawable.ic_feedback else R.drawable.ic_outline_feedback
            )

            // Feedback rating stars if available

        }
    }

    private fun formatDate(dateString: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val outputFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
            val date = inputFormat.parse(dateString)
            outputFormat.format(date)
        } catch (e: Exception) {
            dateString
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<SessionHistoryItem>() {
        override fun areItemsTheSame(oldItem: SessionHistoryItem, newItem: SessionHistoryItem) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: SessionHistoryItem, newItem: SessionHistoryItem) =
            oldItem == newItem
    }
}