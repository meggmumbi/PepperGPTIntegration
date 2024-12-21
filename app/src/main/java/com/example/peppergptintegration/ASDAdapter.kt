package com.example.peppergptintegration

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ASDAdapter(
    private val asdList: List<ASDType>,
    private val onClick: (ASDType) -> Unit
) : RecyclerView.Adapter<ASDAdapter.ASDViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ASDViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.card_asd_item, parent, false)
        return ASDViewHolder(view)
    }

    override fun onBindViewHolder(holder: ASDViewHolder, position: Int) {
        val asdType = asdList[position]
        holder.title.text = asdType.title
        holder.description.text = asdType.description
        holder.image.setImageResource(asdType.imageResId)

        holder.itemView.setOnClickListener {
            onClick(asdType)
        }
    }

    override fun getItemCount(): Int = asdList.size

    class ASDViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.title)
        val description: TextView = itemView.findViewById(R.id.description)
        val image: ImageView = itemView.findViewById(R.id.icon)
    }
}