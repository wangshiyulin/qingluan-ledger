package com.qingluan.ledger

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*

/** OEM APK containers may advertise a photo picker without providing its activity. */
@Composable
fun rememberImagePicker(onImage: (Uri) -> Unit, onError: () -> Unit): () -> Unit {
    val receive by rememberUpdatedState(onImage)
    val failure by rememberUpdatedState(onError)
    val document =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) receive(uri)
        }
    val photos =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) receive(uri)
        }
    return {
        try {
            photos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        } catch (_: Exception) {
            try {
                document.launch(arrayOf("image/*"))
            } catch (_: Exception) {
                failure()
            }
        }
    }
}
