package org.sorz.lab.tinykeepass.ui

import android.widget.ImageView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.kunzisoft.keepass.database.element.Entry
import com.kunzisoft.keepass.icons.IconDrawableFactory
import org.sorz.lab.tinykeepass.R
import org.sorz.lab.tinykeepass.keepass.*

private const val TAG = "ListScreen"

@Preview(showSystemUi = true)
@Composable
private fun ListScreenPreview() {
    ListScreen(RealRepository(LocalContext.current))
}

@Composable
fun ListScreen(
    repo: Repository,
    nav: NavController? = null,
) {
    val dbState by repo.databaseState.collectAsState()
    val iconFactory = remember { repo.iconFactory }
    val entries by repo.databaseEntries.collectAsState()

    if (dbState != DatabaseState.UNLOCKED && nav != null) {
        NavActions(nav).locked()
    }

    LazyColumn(
        Modifier.fillMaxWidth()
    ) {
        items(
            items = entries,
            key = { it.nodeId }
        ) { entry ->
            EntryDetail(iconFactory, entry, false)
        }
    }

}


@Composable
private fun EntryDetail(iconFactory: IconDrawableFactory, entry: Entry, expanded: Boolean) {
    val context = LocalContext.current
    val iconDrawable = iconFactory.getIconSuperDrawable(context, entry.icon, 24).drawable
    Column(Modifier.fillMaxWidth()) {
        Row {
            // Icon
            AndroidView(factory = {
                ImageView(context).apply {
                    setImageDrawable(iconDrawable)
                }
            })
            Spacer(Modifier.width(16.dp))
            Column {
                // Title
                Text(
                    text = entry.title,
                    fontSize = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row {
                    // Username
                    if (entry.username != "") {
                        Text(
                            text = entry.username,
                            maxLines = 1,
                        )
                    }
                    // URL
                    if (entry.url != "") {
                        val color = colorResource(android.R.color.darker_gray)
                        Text(
                            text = entry.urlHostname,
                            maxLines = 1,
                            color = color,
                        )
                        entry.urlPath?.let { path ->
                            Text(
                                text = entry.urlHostname,
                                color = color.copy(alpha = 0.6f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    
        if (expanded) {
            Text(
                text = entry.password,
                textAlign = TextAlign.Center,
                fontFamily = FontFamily(
                    Font(R.font.fira_mono_regular)
                ),
            )
        }
    }
}