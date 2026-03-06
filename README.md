# 🚦 TrafficNow

![Android](https://img.shields.io/badge/Platform-Android-green)
![Kotlin](https://img.shields.io/badge/Language-Kotlin-blue) ![Google
Maps](https://img.shields.io/badge/API-Google%20Maps-orange)
![Status](https://img.shields.io/badge/Status-Active-brightgreen)

**TrafficNow** is a real-time urban navigation Android application that
helps commuters make smarter travel decisions by combining **live
traffic conditions, multimodal route planning, and nearby public transit
information** into a single interactive map interface.

The app integrates **Google Maps Platform APIs** and modern Android
development practices to deliver a smooth, map‑centric commuting
experience.

------------------------------------------------------------------------

# 📱 Demo

🎥 Demo Video\
https://youtube.com/shorts/rScpCKN2138?feature=share

🎨 UI Design\
https://www.figma.com/design/KPiDmaT84ZCeBZXdd2JA5l/TrafficNow-UI

------------------------------------------------------------------------

# ✨ Key Features

## 🗺 Real-Time Traffic Map

-   Integrated **Google Maps traffic layer**
-   Color‑coded traffic visualization
-   Interactive traffic toggle

## 🚗 Multi‑Modal Route Planning

Supports:

-   🚗 Driving\
-   🚶 Walking\
-   🚆 Public transit

Uses **Google Directions API** to compute routes with real‑time traffic
conditions.

## 📍 Smart Location & Navigation

-   GPS tracking using **FusedLocationProviderClient**
-   Automatic map camera adjustment
-   Accurate current location marker

## 🔎 Destination Search

-   Powered by **Google Places API**
-   Autocomplete search
-   Nearby‑biased results

## 🚏 Public Transit Discovery

Detects nearby: - Bus stops - Metro stations - Train stations

Features: - Categorized transit markers - Simulated real‑time arrivals

## 🚶 Motion‑Aware Interface

Uses **Android accelerometer sensors** to detect:

-   Walking
-   Driving
-   Stationary

The interface adapts dynamically based on user motion state.

------------------------------------------------------------------------

# 🏗 Architecture

TrafficNow follows a **Model--View--Controller (MVC)** architecture to
maintain modularity and scalability.

    TrafficNow
    │
    ├── MainActivity
    │   └── Map control & UI coordination
    │
    ├── Models
    │   ├── Route
    │   ├── TransitStop
    │   └── MotionState
    │
    ├── Adapters
    │   ├── RouteAdapter
    │   └── TransitAdapter
    │
    ├── Services
    │   ├── Google Maps API
    │   ├── Google Directions API
    │   └── Transit API
    │
    └── Utils
        └── MotionDetector

Asynchronous API requests are handled using **Kotlin Coroutines**,
ensuring responsive UI performance.

------------------------------------------------------------------------

# 🛠 Tech Stack

## Mobile

-   Kotlin
-   Android SDK
-   Material Design 3

## APIs

-   Google Maps API
-   Google Directions API
-   Google Places API
-   TransitLand API

## Libraries

-   Retrofit
-   Kotlin Coroutines
-   Google Play Services

------------------------------------------------------------------------

# ⚡ Performance Optimizations

Several strategies were implemented to ensure performance and
reliability:

-   API request caching
-   Request throttling to reduce quota usage (\~60% reduction)
-   Efficient polyline decoding for route rendering
-   Background API processing using Kotlin coroutines
-   Optimized sensor sampling to reduce battery consumption

------------------------------------------------------------------------

# 🧪 Testing

## Unit Testing

-   API integration validation
-   Data model serialization

## Integration Testing

-   Location services
-   Route planning accuracy
-   Map interaction

## Performance Testing

  Metric              Result
  ------------------- ---------------
  App Launch          \< 2 seconds
  Route Calculation   \~1.5 seconds
  Map FPS             \~60fps

------------------------------------------------------------------------

# 🚀 Future Improvements

Potential roadmap items:

-   Real‑time transit APIs (OneBusAway)
-   Offline navigation support
-   Multi‑city transit support
-   Route personalization
-   Social route sharing
-   Machine‑learning‑based route optimization

------------------------------------------------------------------------

# 📂 Project Structure

    app
     ├── activities
     ├── adapters
     ├── models
     ├── services
     ├── utils
     └── resources

------------------------------------------------------------------------

# 👨‍💻 Author

**Yizhao Li**\
M.S. Electrical & Computer Engineering\
University of Washington

------------------------------------------------------------------------

⭐ If you find this project interesting, feel free to star the
repository!
