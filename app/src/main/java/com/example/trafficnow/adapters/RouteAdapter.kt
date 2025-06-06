// app/src/main/java/com/example/trafficnow/adapters/RouteAdapter.kt
package com.example.trafficnow.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.trafficnow.R
import com.example.trafficnow.models.Route
import com.example.trafficnow.models.RouteType

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
        private val routeIcon: ImageView = itemView.findViewById(R.id.routeIcon)
        private val routeIconBackground: View = itemView.findViewById(R.id.routeIconBackground)
        private val modeTextView: TextView = itemView.findViewById(R.id.routeMode)
        private val durationTextView: TextView = itemView.findViewById(R.id.routeDuration)
        private val distanceTextView: TextView = itemView.findViewById(R.id.routeDistance)
        private val descriptionTextView: TextView = itemView.findViewById(R.id.routeDescription)

        fun bind(route: Route) {
            modeTextView.text = route.mode
            durationTextView.text = route.duration
            distanceTextView.text = route.distance
            descriptionTextView.text = route.description

            // 根据路线类型设置图标和颜色
            when (route.type) {
                RouteType.DRIVING -> {
                    routeIcon.setImageResource(R.drawable.ic_directions_car)
                    routeIconBackground.backgroundTintList =
                        ContextCompat.getColorStateList(itemView.context, R.color.primary_color)
                    durationTextView.setTextColor(
                        ContextCompat.getColor(itemView.context, R.color.primary_color)
                    )
                }
                RouteType.WALKING -> {
                    routeIcon.setImageResource(R.drawable.ic_directions_walk)
                    routeIconBackground.backgroundTintList =
                        ContextCompat.getColorStateList(itemView.context, R.color.secondary_color)
                    durationTextView.setTextColor(
                        ContextCompat.getColor(itemView.context, R.color.secondary_color)
                    )
                }
                RouteType.TRANSIT -> {
                    routeIcon.setImageResource(R.drawable.ic_directions_bus)
                    routeIconBackground.backgroundTintList =
                        ContextCompat.getColorStateList(itemView.context, R.color.accent_color)
                    durationTextView.setTextColor(
                        ContextCompat.getColor(itemView.context, R.color.accent_color)
                    )
                }
            }

            // 点击事件
            itemView.setOnClickListener {
                onRouteSelected(route)
            }
        }
    }
}