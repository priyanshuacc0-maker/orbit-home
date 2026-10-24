package com.orbit.home.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.orbit.home.R
import com.orbit.home.ui.theme.OrbitColors

/** Functional search field that filters the app list. */
@Composable
fun OrbitSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    /** Runs when the keyboard's Search key is pressed. */
    onSearch: () -> Unit = {},
    /** Optional requester, so the drawer can open with the field focused (P2). */
    focusRequester: FocusRequester? = null,
    /** The field's outline. The drawer asks for a full pill. */
    shape: Shape = RoundedCornerShape(18.dp),
    /** Draws no border until the field is focused. Off, the field keeps its quiet grey outline. */
    quietBorder: Boolean = false
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier),
        singleLine = true,
        shape = shape,
        placeholder = {
            Text(text = stringResource(R.string.search_hint), color = OrbitColors.TextSecondary)
        },
        leadingIcon = {
            Icon(Icons.Filled.Search, contentDescription = null, tint = OrbitColors.TextSecondary)
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.search_clear),
                        tint = OrbitColors.TextSecondary
                    )
                }
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = OrbitColors.Surface,
            unfocusedContainerColor = OrbitColors.Surface,
            focusedBorderColor = OrbitColors.Accent,
            unfocusedBorderColor = if (quietBorder) Color.Transparent else OrbitColors.Guide,
            focusedTextColor = OrbitColors.TextPrimary,
            unfocusedTextColor = OrbitColors.TextPrimary,
            cursorColor = OrbitColors.Accent
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        // The Search key was declared but had no action attached, so pressing it
        // did nothing at all (P0-12).
        keyboardActions = KeyboardActions(onSearch = { onSearch() })
    )
}
