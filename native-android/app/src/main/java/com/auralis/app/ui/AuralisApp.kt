package com.auralis.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Material
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auralis.app.ui.theme.StudioAccent
import com.auralis.app.ui.theme.StudioBlack
import com.auralis.app.ui.theme.StudioLine
import com.auralis.app.ui.theme.StudioMuted
import com.auralis.app.ui.theme.StudioPanel
import com.auralis.app.ui.theme.StudioRaised
import com.auralis.app.ui.theme.StudioWhite

@Composable
fun AuralisApp() {
    var selectedTab by remember { mutableIntStateOf(0) }
    Scaffold(
        containerColor = StudioBlack,
        bottomBar = {
            NavigationBar(containerColor = StudioPanel, contentColor = StudioWhite) {
                val tabs = listOf("STUDIO", "LAYERS", "AUDIO", "SETTINGS")
                val icons = listOf(Icons.Outlined.GraphicEq, Icons.Outlined.Layers, Icons.Outlined.AudioFile, Icons.Outlined.Settings)
                tabs.forEachIndexed { index, label ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(icons[index], contentDescription = label, modifier = Modifier.size(19.dp)) },
                        label = { Text(label, fontSize = 9.sp, fontWeight = FontWeight.SemiBold) }
                    )
                }
            }
        }
    ) { insets ->
        Column(
            modifier = Modifier.fillMaxSize().padding(insets).background(StudioBlack).padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(54.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("AURALIS", color = StudioWhite, fontSize = 15.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                Spacer(Modifier.weight(1f))
                Text("NATIVE / 0.1", color = StudioMuted, fontSize = 10.sp, letterSpacing = 1.sp)
            }
            Text("VISUALIZER WORKSPACE", color = StudioMuted, fontSize = 10.sp, letterSpacing = 1.5.sp)
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f)
                    .background(
                        Brush.linearGradient(listOf(StudioRaised, StudioBlack, StudioPanel)),
                        RoundedCornerShape(4.dp)
                    )
                    .padding(1.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Outlined.GraphicEq, contentDescription = null, tint = StudioAccent, modifier = Modifier.size(48.dp))
                    Text("YOUR CANVAS", color = StudioWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Text("Import a track to start building", color = StudioMuted, fontSize = 11.sp)
                }
            }
            Surface(
                color = StudioPanel,
                shape = RoundedCornerShape(4.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, StudioLine),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("NO TRACK LOADED", color = StudioWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("Audio engine idle", color = StudioMuted, fontSize = 10.sp)
                        }
                        Spacer(Modifier.weight(1f))
                        Text("00:00", color = StudioMuted, fontSize = 11.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = { },
                            colors = ButtonDefaults.buttonColors(containerColor = StudioAccent, contentColor = StudioBlack),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Outlined.AudioFile, contentDescription = null, modifier = Modifier.size(17.dp))
                            Text("  IMPORT AUDIO", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        Surface(color = StudioRaised, shape = RoundedCornerShape(4.dp)) {
                            Text("PLAY", color = StudioMuted, modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp), fontSize = 10.sp)
                        }
                    }
                }
            }
            Text("ENGINE INITIALIZATION  •  COMPOSE / MEDIA3", color = StudioMuted, fontSize = 9.sp, modifier = Modifier.padding(bottom = 4.dp))
        }
    }
}
