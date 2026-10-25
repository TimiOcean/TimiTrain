package de.timitrain.ui.planen

import android.Manifest
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.timitrain.R
import de.timitrain.data.ExercisePhotos

@Composable
fun ExerciseNoteAndPhoto(
    note: String,
    photoName: String?,
    onNote: (String) -> Unit,
    onPhoto: (String?) -> Unit,
) {
    val context = LocalContext.current
    var pendingCameraUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onPhoto(ExercisePhotos.copyIn(context, uri))
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val pending = pendingCameraUri
        pendingCameraUri = null
        if (saved && pending != null) onPhoto(ExercisePhotos.copyIn(context, pending))
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) return@rememberLauncherForActivityResult
        val uri = ExercisePhotos.cameraUri(context)
        pendingCameraUri = uri
        camera.launch(uri)
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = note,
            onValueChange = onNote,
            label = { Text(stringResource(R.string.note)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(stringResource(R.string.photo))
        val bitmap = remember(photoName) {
            photoName?.let { name ->
                val file = ExercisePhotos.file(context, name)
                if (!file.exists()) null else BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
            }
        }
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = stringResource(R.string.photo),
                modifier = Modifier.fillMaxWidth().height(180.dp),
                contentScale = ContentScale.Crop,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }) { Text(stringResource(R.string.photo_gallery)) }
            OutlinedButton(onClick = {
                permission.launch(Manifest.permission.CAMERA)
            }) { Text(stringResource(R.string.photo_camera)) }
        }
        if (photoName != null) {
            OutlinedButton(onClick = { onPhoto(null) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.photo_remove))
            }
        }
    }
}
