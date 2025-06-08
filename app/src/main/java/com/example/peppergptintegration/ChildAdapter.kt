package com.example.peppergptintegration

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.peppergptintegration.R
import com.example.peppergptintegration.Child
class ChildAdapter(
    private var children: List<Child>,
    private val onItemClick: (Child) -> Unit,
    private val onTherapyClick: (Child) -> Unit,
    private val onViewProfileClick: (Child) -> Unit
) : RecyclerView.Adapter<ChildAdapter.ChildViewHolder>() {

    inner class ChildViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameView: TextView = itemView.findViewById(R.id.childName)
        private val ageView: TextView = itemView.findViewById(R.id.childAge)
        private val dateView: TextView = itemView.findViewById(R.id.diagnosisDate)
        private val notesView: TextView = itemView.findViewById(R.id.childNotes)
        private val therapyButton: TextView = itemView.findViewById(R.id.startTherapyButton)
        private val viewDetailsButton: TextView = itemView.findViewById(R.id.viewChildDetailsButton)

        fun bind(child: Child) {
            nameView.text = child.name
            ageView.text = "${child.age} years old"
            dateView.text = "Diagnosed: ${child.diagnosisDate}"
            notesView.text = child.notes

            itemView.setOnClickListener { onItemClick(child) }
            therapyButton.setOnClickListener { onTherapyClick(child) }
            viewDetailsButton.setOnClickListener { onViewProfileClick(child) }
        }
    }

    fun updateChildren(newChildren: List<Child>) {
        children = newChildren
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChildViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_child, parent, false)
        return ChildViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChildViewHolder, position: Int) {
        holder.bind(children[position])
    }

    override fun getItemCount() = children.size
}