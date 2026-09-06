package com.veltravia.marketai.media

import androidx.compose.runtime.Composable

/**
 * Chart screenshot picker:
 *  - Android: system Photo Picker (PickVisualMedia)
 *  - iOS: PHPickerViewController
 * Both downscale to max 1600px on the long edge, JPEG-85, and return a
 * "data:image/jpeg;base64,..." URL ready for analysis.
 */
@Composable
expect fun rememberChartImagePicker(): ChartImagePicker

class ChartImagePicker internal expect constructor() {
    /** Opens the platform picker; onResult receives the base64 data URL or null if cancelled. */
    expect fun launch(onResult: (String?) -> Unit)
}
