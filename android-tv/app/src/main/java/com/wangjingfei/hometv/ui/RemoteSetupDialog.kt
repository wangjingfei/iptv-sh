package com.wangjingfei.hometv.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.wangjingfei.hometv.R
import com.wangjingfei.hometv.remote.RemoteSetupPhase
import com.wangjingfei.hometv.remote.RemoteSetupSession

@Composable
internal fun RemoteSetupDialog(
    session: RemoteSetupSession,
    canApply: Boolean,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
) {
    val qrBitmap = remember(session.url) { runCatching { createQrBitmap(session.url) }.getOrNull() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.phone_setup), fontSize = 24.sp, fontWeight = FontWeight.Bold) },
        text = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                if (qrBitmap != null) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = stringResource(R.string.phone_setup_qr),
                        modifier = Modifier.size(238.dp)
                            .background(Color.White, RoundedCornerShape(6.dp))
                            .padding(10.dp)
                            .testTag("remote-setup-qr"),
                    )
                }
                Column(Modifier.width(270.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = session.message,
                        color = when (session.phase) {
                            RemoteSetupPhase.SUCCESS -> Mint
                            RemoteSetupPhase.ERROR -> ErrorRed
                            RemoteSetupPhase.WAITING, RemoteSetupPhase.TESTING -> TextPrimary
                        },
                        fontSize = 19.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = session.url,
                        color = TextMuted,
                        fontSize = 18.sp,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(stringResource(R.string.phone_setup_expiry), color = TextMuted, fontSize = 18.sp)
                }
            }
        },
        confirmButton = {
            if (canApply) {
                TvActionButton(onClick = onApply, modifier = Modifier.testTag("remote-setup-apply")) {
                    Text(stringResource(R.string.apply_tested_source))
                }
            }
        },
        dismissButton = {
            TvActionButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
        shape = RoundedCornerShape(6.dp),
        containerColor = Panel,
    )
}

internal fun createQrBitmap(value: String, size: Int = 640): Bitmap {
    require(size > 0)
    val matrix = MultiFormatWriter().encode(value, BarcodeFormat.QR_CODE, size, size)
    val pixels = IntArray(size * size)
    for (y in 0 until size) {
        for (x in 0 until size) {
            pixels[y * size + x] = if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        }
    }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}
