package com.glycemicgpt.wear.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.glycemicgpt.wear.data.WatchDataRepository

class IoBDetailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                IoBDetailScreen()
            }
        }
    }
}

@Composable
private fun IoBDetailScreen() {
    val iobState by WatchDataRepository.iob.collectAsState()
    val currentIoB = iobState?.iob ?: 0f

    Scaffold(
        timeText = { TimeText() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "IoB Detail",
                fontSize = 14.sp,
                color = Color.Gray,
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (iobState == null) {
                Text(
                    text = "No data",
                    fontSize = 18.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                )
            } else {
                DecayRow("Now", currentIoB)
                DecayRow("+30m", IoBProjection.projectIoB(currentIoB, 30))
                DecayRow("+60m", IoBProjection.projectIoB(currentIoB, 60))
                DecayRow("+120m", IoBProjection.projectIoB(currentIoB, 120))

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "DIA: 5h",
                    fontSize = 11.sp,
                    color = Color.Gray,
                )
            }
        }
    }
}

@Composable
private fun DecayRow(label: String, iob: Float) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = Color.Gray,
        )
        Text(
            text = "%.2f u".format(iob),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
    }
}
