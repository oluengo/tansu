package io.github.oluengo.tansu.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.oluengo.tansu.R
import io.github.oluengo.tansu.data.JsonView
import io.github.oluengo.tansu.ui.theme.Ts
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date

/**
 * Vista «Visual» del contenido JSON: fichas clave → valor, árboles de carpetas, tablas, etiquetas,
 * fechas legibles y sí/no. Solo para leer: no cambia los datos. Reglas en JsonView (core).
 */
@Composable
fun JsonVisual(root: JsonElement, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 14.dp, vertical = 8.dp)) { Node(root, null, "r") }
}

@Composable
private fun Node(e: JsonElement, key: String?, path: String) {
    when (e) {
        is JsonObject -> ObjectView(e, path)
        is JsonArray -> ArrayView(e, path)
        else -> Value(e, key)
    }
}

@Composable
private fun ObjectView(o: JsonObject, path: String) {
    val flat = o.entries.filter { JsonView.isFlat(it.value) }
    val nested = o.entries.filterNot { JsonView.isFlat(it.value) }
    if (o.isEmpty()) { Empty(stringResource(R.string.value_empty_text)); return }
    if (flat.isNotEmpty()) Column {
        flat.forEachIndexed { i, (k, v) ->
            if (i > 0) HorizontalDivider(color = Ts.colors.line)
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(JsonView.human(k), color = Ts.colors.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.38f).padding(top = 2.dp))
                Box(Modifier.weight(0.62f)) { if (v is JsonArray) Chips(v) else Value(v, k) }
            }
        }
    }
    nested.forEach { (k, v) -> Section(JsonView.human(k), v, "$path/$k", startOpen = nested.size <= 3) }
}

@Composable
private fun Section(title: String, v: JsonElement, path: String, startOpen: Boolean) {
    var open by rememberSaveable(path) { mutableStateOf(startOpen) }
    val ctx = LocalContext.current
    val count = when (v) {
        is JsonArray -> v.size.toString()
        is JsonObject -> ctx.resources.getQuantityString(R.plurals.fields, v.size, v.size)
        else -> ""
    }
    Surface(
        shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Ts.colors.line), color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
    ) {
        Column(Modifier.animateContentSize()) {
            Row(Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 10.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (open) Icons.Outlined.KeyboardArrowDown else Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = Ts.colors.muted, modifier = Modifier.size(18.dp))
                Text(title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 6.dp))
                Text(count, color = Ts.colors.muted, style = MaterialTheme.typography.bodySmall)
            }
            if (open) Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp)) { Node(v, null, path) }
        }
    }
}

@Composable
private fun ArrayView(a: JsonArray, path: String) {
    when {
        a.isEmpty() -> Empty(stringResource(R.string.value_empty_list))
        JsonView.isChips(a) -> Chips(a)
        a.all(JsonView::isPrim) -> Column { a.forEachIndexed { i, v -> Row { Text("${i + 1}.", color = Ts.colors.muted, modifier = Modifier.width(28.dp)); Value(v, null) } } }
        JsonView.isTree(a) -> Tree(a, path, 0)
        JsonView.isTable(a) -> Table(a, path)
        else -> a.forEachIndexed { i, v -> Section((v as? JsonObject)?.let(JsonView::titleOf) ?: "#${i + 1}", v, "$path/$i", startOpen = false) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Chips(a: JsonArray) {
    if (a.isEmpty()) { Empty(stringResource(R.string.value_empty_list)); return }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        a.forEach { Chip((it as? JsonPrimitive)?.content ?: it.toString(), MaterialTheme.colorScheme.primary, Ts.colors.accentSoft) }
    }
}

@Composable
private fun Chip(text: String, fg: Color, bg: Color) {
    Text(text, color = fg, fontSize = 12.5.sp, modifier = Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 9.dp, vertical = 3.dp))
}

@Composable
private fun Empty(text: String) { Text(text, color = Ts.colors.muted, style = MaterialTheme.typography.bodySmall) }

/** Valor simple: sí/no, vacío, fecha, número, enlace o texto. */
@Composable
private fun Value(e: JsonElement, key: String?) {
    if (e is JsonNull) { Text("—", color = Ts.colors.muted); return }
    val p = e as? JsonPrimitive ?: run { Text(e.toString(), fontFamily = FontFamily.Monospace, fontSize = 12.sp); return }
    JsonView.bool(p)?.let { b ->
        if (b) Chip("✓ " + stringResource(R.string.value_yes), Color(0xFF2E8A5A), Color(0x262E8A5A))
        else Chip("✗ " + stringResource(R.string.value_no), Ts.colors.danger, Ts.colors.danger.copy(alpha = 0.12f))
        return
    }
    JsonView.dateMillis(p, key)?.let { ms ->
        Column {
            Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(ms)), style = MaterialTheme.typography.bodyMedium)
            Text(p.content, color = Ts.colors.muted, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        }
        return
    }
    if (!p.isString) {
        val n = p.content.toBigDecimalOrNull()
        Text(n?.let { NumberFormat.getInstance().format(it) } ?: p.content, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
        return
    }
    val s = p.content
    when {
        s.isEmpty() -> Text(stringResource(R.string.value_empty_text), color = Ts.colors.muted)
        JsonView.isUrl(s) -> {
            val uri = LocalUriHandler.current
            Text(s, color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.clickable { runCatching { uri.openUri(s) } })
        }
        s.startsWith("wss://") || s.startsWith("ws://") -> Text(s, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
        else -> Text(s, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Carpetas con hijos (p. ej. favoritos) como árbol. */
@Composable
private fun Tree(a: JsonArray, path: String, depth: Int) {
    Column(Modifier.padding(start = if (depth > 0) 14.dp else 0.dp)) {
        a.forEachIndexed { i, el ->
            val o = el as? JsonObject ?: return@forEachIndexed
            val ck = JsonView.childrenKey(o)
            val kids = ck?.let { o[it] as JsonArray }
            val title = JsonView.titleOf(o) ?: "#${i + 1}"
            val meta = o.entries.filter { (k, v) -> k != ck && v is JsonPrimitive && v !is JsonNull && v.content != title }
                .joinToString(" · ") { (k, v) -> JsonView.human(k) + ": " + (v as JsonPrimitive).content }
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val folder = kids != null
                Box(
                    Modifier.size(28.dp).clip(RoundedCornerShape(7.dp)).background(if (folder) Ts.colors.folderSoft else Ts.colors.accentSoft),
                    contentAlignment = Alignment.Center,
                ) { Icon(if (folder) Icons.Outlined.Folder else Icons.Outlined.Description, null, tint = if (folder) Ts.colors.folder else MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp)) }
                Column(Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.SemiBold)
                    if (meta.isNotEmpty()) Text(meta, color = Ts.colors.muted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (kids != null) Text(kids.size.toString(), color = Ts.colors.muted, style = MaterialTheme.typography.bodySmall)
            }
            if (kids != null) {
                when {
                    kids.isEmpty() -> Box(Modifier.padding(start = 36.dp)) { Empty(stringResource(R.string.value_empty_list)) }
                    kids.all { it is JsonObject && JsonView.titleOf(it) != null } -> Tree(kids, "$path/$i", depth + 1)
                    else -> Box(Modifier.padding(start = 36.dp)) { Node(kids, null, "$path/$i") }
                }
            }
        }
    }
}

/** Lista de objetos simples como tabla (desplazable a lo ancho). Muestra las primeras filas y «Ver todas». */
@Composable
private fun Table(a: JsonArray, path: String) {
    val cols = JsonView.columns(a)
    var all by rememberSaveable(path) { mutableStateOf(false) }
    val rows = if (all) a else a.take(JsonView.TABLE_ROWS)
    val w = 132.dp
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, Ts.colors.line, RoundedCornerShape(12.dp))) {
        Column(Modifier.horizontalScroll(rememberScrollState())) {
            Row(Modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
                cols.forEach { Text(JsonView.human(it), color = Ts.colors.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(w).padding(horizontal = 10.dp, vertical = 7.dp)) }
            }
            rows.forEach { r ->
                val o = r as JsonObject
                HorizontalDivider(color = Ts.colors.line, modifier = Modifier.width(w * cols.size))
                Row {
                    cols.forEach { k ->
                        Box(Modifier.width(w).padding(horizontal = 10.dp, vertical = 7.dp)) {
                            when (val v = o[k]) {
                                null -> Unit
                                is JsonArray -> Text(v.joinToString(", ") { (it as? JsonPrimitive)?.content ?: it.toString() }, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                else -> CellValue(v, k)
                            }
                        }
                    }
                }
            }
        }
        if (!all && a.size > JsonView.TABLE_ROWS) {
            HorizontalDivider(color = Ts.colors.line)
            TextButton(onClick = { all = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.view_rows, a.size)) }
        }
    }
}

/** Celda de tabla: como Value pero en una línea. */
@Composable
private fun CellValue(v: JsonElement, key: String) {
    val p = v as? JsonPrimitive
    when {
        p == null || p is JsonNull -> Text("—", color = Ts.colors.muted, fontSize = 13.sp)
        JsonView.bool(p) != null -> if (JsonView.bool(p) == true) Text("✓", color = Color(0xFF2E8A5A), fontWeight = FontWeight.Bold) else Text("✗", color = Ts.colors.danger, fontWeight = FontWeight.Bold)
        JsonView.dateMillis(p, key) != null -> Text(DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(JsonView.dateMillis(p, key)!!)), fontSize = 13.sp, maxLines = 1)
        else -> Text(p.content, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = if (p.isString) null else FontFamily.Monospace)
    }
}
