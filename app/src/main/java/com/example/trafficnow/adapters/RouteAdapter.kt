// app/src/main/java/com/example/trafficnow/adapters/RouteAdapter.kt
package com.example.trafficnow.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.trafficnow.R
import com.example.trafficnow.models.Route

class RouteAdapter(private val onRouteSelected: (Route) -> Unit) :
    RecyclerView.Adapter<RouteAdapter.RouteViewHolder>() {

    private val routes = mutableListOf<Route>()

    fun updateRoutes(newRoutes: List<Route>) {
        routes.clear()
        routes.addAll(newRoutes)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RouteViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_route, parent, false)
        return RouteViewHolder(view)
    }

    override fun onBindViewHolder(holder: RouteViewHolder, position: Int) {
        holder.bind(routes[position])
    }

    override fun getItemCount() = routes.size

    inner class RouteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val modeTextView: TextView = itemView.findViewById(R.id.routeMode)
        private val durationTextView: TextView = itemView.findViewById(R.id.routeDuration)
        private val distanceTextView: TextView = itemView.findViewById(R.id.routeDistance)
        private val descriptionTextView: TextView = itemView.findViewById(R.id.routeDescription)

        fun bind(route: Route) {
            modeTextView.text = route.mode
            durationTextView.text = route.duration
            distanceTextView.text = route.distance
            descriptionTextView.text = route.description

            itemView.setOnClickListener {
                onRouteSelected(route)
            }
        }
    }
}