// app/src/main/java/com/example/trafficnow/adapters/TransitAdapter.kt
package com.example.trafficnow.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.trafficnow.R

class TransitAdapter : RecyclerView.Adapter<TransitAdapter.TransitViewHolder>() {

    private val arrivals = mutableListOf<String>()

    fun updateArrivals(newArrivals: List<String>) {
        arrivals.clear()
        arrivals.addAll(newArrivals)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransitViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_transit, parent, false)
        return TransitViewHolder(view)
    }

    override fun onBindViewHolder(holder: TransitViewHolder, position: Int) {
        holder.bind(arrivals[position])
    }

    override fun getItemCount() = arrivals.size

    inner class TransitViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val arrivalTextView: TextView = itemView.findViewById(R.id.arrivalInfo)

        fun bind(arrival: String) {
            arrivalTextView.text = arrival
        }
    }
}