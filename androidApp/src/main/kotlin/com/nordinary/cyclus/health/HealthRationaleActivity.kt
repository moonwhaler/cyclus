package com.nordinary.cyclus.health

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nordinary.cyclus.R
import com.nordinary.cyclus.ui.theme.Cyclus
import com.nordinary.cyclus.ui.theme.CyclusTheme
import com.nordinary.cyclus.ui.today.PrimaryButton

/**
 * Why Cyclus asks for Health Connect access (shown by Health Connect / the system permission screens). Static text,
 * no data and no app lock involved, so it is safe to export and reviewers can read it.
 */
class HealthRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            CyclusTheme {
                Surface(Modifier.fillMaxSize(), color = Cyclus.colors.bg) {
                    Column(
                        Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp).widthIn(max = 640.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text(stringResource(R.string.hr_title), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Cyclus.colors.ink)
                        for (id in listOf(R.string.hr_read, R.string.hr_write, R.string.hr_local, R.string.hr_revoke)) {
                            Text(stringResource(id), color = Cyclus.colors.ink2, fontSize = 15.sp)
                        }
                        PrimaryButton(stringResource(R.string.hr_close), { finish() })
                    }
                }
            }
        }
    }
}
