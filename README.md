# Voronoi Wallpaper

A live wallpaper app for Android that generates dynamic Voronoi diagrams, creating a constantly shifting and visually engaging background.

## Overview

This app utilizes the Voronoi diagram algorithm to create a live wallpaper that dynamically changes over time. Each Voronoi diagram is generated from a set of control points that move around the screen, leading to a unique pattern every time. The colors of the Voronoi cells are generated to be distinct and pleasing to the eye, with an adaptive contrast enhancement to make them stand out.

## Features

- **Dynamic Voronoi Generation:** The wallpaper continuously generates Voronoi diagrams, ensuring that the background is never static.
- **Moving Control Points:** The points that define the Voronoi cells move around the screen, providing a smooth and fluid animation.
- **Distinct Colors:** Each Voronoi cell has a unique color, making each diagram visually distinct.
- **Adaptive Contrast:** The colors are generated with adaptive contrast enhancement, ensuring that light and dark colors are balanced for better visibility.
- **Triple-Tap Pause/Resume:** A triple-tap gesture on the screen pauses or resumes the animation, giving users control over the live wallpaper's behavior.
- **Optimized Performance:** The app uses a pixel-stepping method and spatial grid optimization to improve performance without sacrificing too much visual quality.
- **Double Buffering:** Implements double buffering for smooth rendering and reduced screen tearing.
- **Configurable Parameters:**  Although not exposed in the UI, internal parameters like `numPoints`, `pixelStep`, and `frameDelay` can be adjusted to fine-tune performance and visual appeal.
- **Spatial Grid**: Optimizes the way the app computes which point is closest to a given pixel in the screen, leading to a significant performance boost.
- **Golden Angle Color Generation**: colors are spread uniformly around the color wheel using the golden angle.
- **Color shuffle**: Avoid having colors close in the color wheel to be next to each other by shuffling the color array after generation.
- **Adaptive Point Color**: The points are automatically lightened or darkened based on their cell color, so they are always visible on the screen.

## Technical Details

- **Kotlin:** The app is written in Kotlin, leveraging its modern features for concurrency and code readability.
- **Coroutines:** Kotlin coroutines are used for concurrent operations, ensuring smooth animation without blocking the UI thread.
- **WallpaperService:** The app extends `WallpaperService` to function as a live wallpaper on Android devices.
- **Double-Buffered Rendering:** The app uses a double-buffering technique to draw to a background buffer and then copy to the screen, making the animation appear seamless.
- **Pixel-Stepping:** The Voronoi calculation is performed on a reduced resolution grid which is then scaled to the screen size. This speeds up computation.
- **Spatial Gridding:** The app uses a spatial grid to speed up the calculation of which point is closest to each pixel.
- **Mutex:** `Mutex` objects are used to protect access to the points and frame buffer pool from being accessed by multiple threads simultaneously.

## Usage

1. **Installation:** Install the app on your Android device.
2. **Set as Wallpaper:** Go to your device's wallpaper settings and select "Live Wallpapers." Choose "Voronoi Wallpaper" from the list.
3. **Enjoy:** The live wallpaper will start running, continuously generating dynamic Voronoi diagrams.
4. **Pause/Resume:** Triple-tap on the screen to pause or resume the animation.

## Customization (Advanced)

While the app doesn't offer a GUI for parameter adjustment, you can modify the following constants in the `VoronoiWallpaperService.kt` file to change the app's behavior:

- `numPoints`: The number of control points in the Voronoi diagram. More points create more complex patterns but may impact performance.
- `pixelStep`: Controls the resolution of the Voronoi calculation. Higher values improve performance at the cost of some visual quality.
- `frameDelay`: Controls the target frame rate.
- `DARK_THRESHOLD`, `LIGHTEN_FACTOR` and `DARKEN_FACTOR`: control the color generation algorithm.

## Dependencies

The project uses the following dependencies:

- **Jetpack Compose**: For a modern declarative UI design.
- `androidx.compose.ui:ui-tooling-android:1.8.0`
- `androidx.compose.runtime:runtime-android:1.8.0`
- `androidx.compose.ui:ui-android:1.8.0`
- `androidx.compose.runtime:runtime-saveable-android:1.8.0`
- `androidx.compose.ui:ui-geometry-android:1.8.0`
- `androidx.compose.ui:ui-graphics-android:1.8.0`
- `androidx.compose.ui:ui-unit-android:1.8.0`
- `androidx.compose.ui:ui-text-android:1.8.0`
- `androidx.compose.ui:ui-util-android:1.8.0`
- `androidx.lifecycle:lifecycle-runtime-compose-android:2.8.7`
- `androidx.compose.ui:ui-tooling-data-android:1.8.0`
- `androidx.compose.ui:ui-tooling-preview-android:1.8.0`
- `androidx.compose.ui:ui-test-manifest:1.8.0`
- `androidx.activity:activity-compose:1.10.1`
- `androidx.compose.material3:material3-android:1.3.2`
- `androidx.compose.foundation:foundation-android:1.8.0`
- `androidx.compose.animation:animation-android:1.8.0`
- `androidx.compose.animation:animation-core-android:1.8.0`
- `androidx.compose.foundation:foundation-layout-android:1.8.0`
- `androidx.compose.material:material-icons-core-android:1.7.8`
- `androidx.compose.material:material-ripple-android:1.8.0`
- `androidx.annotation:annotation-experimental:1.4.1`
- `androidx.lifecycle:lifecycle-runtime-android:2.8.7`
- `androidx.lifecycle:lifecycle-runtime-ktx-android:2.8.7`
- `androidx.activity:activity:1.10.1`

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Further Reading

- [Voronoi Diagram - Wikipedia](https://en.wikipedia.org/wiki/Voronoi_diagram)
- [Android Developer Documentation](https://developer.android.com/)
- [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html)

## Contributing

We appreciate contributions to the Voronoi Wallpaper project! Here's how you can help:

**Ways to Contribute:**

- **Bug Reports:** If you find a bug, please open an issue on the issue tracker with a detailed description of the problem and steps to reproduce it.
- **Feature Requests:** If you have an idea for a new feature, feel free to open an issue to discuss it.
- **Code Contributions:**
    1.  Fork the repository.
    2.  Create a new branch (e.g., `git checkout -b feature/my-new-feature`).
    3.  Make your changes, following the existing code style.
    4.  Commit your changes with clear and concise messages.
    5.  Submit a pull request to the `main` branch.

**Code Style:**

Please try to follow the existing code style. Consistency helps maintain the quality of the code.

**Pull Requests:**

* Ensure your pull request is based on the `main` branch.
* Describe the changes you've made and why they're important.
* If your changes address a specific issue, reference that issue in your pull request description.

We're excited to see your contributions!