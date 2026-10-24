package dev.sadeghtahani.rhinochat.welcome

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.sadeghtahani.rhinochat.theme.RhinoChatTheme
import org.jetbrains.compose.resources.stringResource
import rhinochat.shared.generated.resources.Res
import rhinochat.shared.generated.resources.app_name
import rhinochat.shared.generated.resources.welcome_message

@Composable
fun WelcomeScreen(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier.safeDrawingPadding().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
internal fun WelcomeRoute() {
    WelcomeScreen(
        title = stringResource(Res.string.app_name),
        message = stringResource(Res.string.welcome_message),
    )
}

@Preview(name = "Welcome · Light", widthDp = 360, heightDp = 640, showBackground = true)
@Composable
private fun WelcomeLightPreview() {
    RhinoChatTheme(darkTheme = false) {
        WelcomeScreen(
            title = stringResource(Res.string.app_name),
            message = stringResource(Res.string.welcome_message),
        )
    }
}

@Preview(name = "Welcome · Dark", widthDp = 360, heightDp = 640, showBackground = true)
@Composable
private fun WelcomeDarkPreview() {
    RhinoChatTheme(darkTheme = true) {
        WelcomeScreen(
            title = stringResource(Res.string.app_name),
            message = stringResource(Res.string.welcome_message),
        )
    }
}
