package com.orbit.home.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.orbit.home.R
import com.orbit.home.ui.theme.OrbitColors
import com.orbit.home.viewmodel.HomeUiState

/**
 * The screen behind the app-drawer icon: make Orbit Home the default launcher, or
 * switch back to the previous one.
 *
 * There is nothing to configure here any more. The wheel shows every installed
 * app, so there is no selection to make, and the dock is seeded from the system's
 * own defaults on first run.
 */
@Composable
fun SetupScreen(
    state: HomeUiState,
    isHome: Boolean,
    onSetHome: () -> Unit,
    onSwitchBack: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.setup_welcome),
            color = OrbitColors.TextPrimary,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.setup_body),
            color = OrbitColors.TextSecondary,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(Modifier.height(28.dp))

        if (isHome) {
            Text(
                text = stringResource(R.string.setup_is_home),
                color = OrbitColors.Accent,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            Button(
                onClick = onSetHome,
                colors = ButtonDefaults.buttonColors(
                    containerColor = OrbitColors.Accent,
                    contentColor = Color.Black
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.setup_set_home))
            }
        }

        Spacer(Modifier.height(10.dp))
        TextButton(
            onClick = onSwitchBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(R.string.setup_switch_back),
                color = OrbitColors.TextSecondary
            )
        }

        Spacer(Modifier.height(20.dp))
        Text(
            // Saying how many apps were found is a cheap, honest way to show that
            // the launcher is working before the user commits to it.
            text = pluralStringResource(R.plurals.setup_app_count, state.allApps.size, state.allApps.size),
            color = OrbitColors.TextSecondary,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall
        )

        Spacer(Modifier.height(20.dp))
        TextButton(onClick = onDone) {
            Text(text = stringResource(R.string.setup_done_button), color = OrbitColors.Accent)
        }
    }
}
