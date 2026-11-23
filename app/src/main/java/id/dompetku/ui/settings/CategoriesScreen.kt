package id.dompetku.ui.settings

import id.dompetku.core.t

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import id.dompetku.data.db.CategoryEntity
import id.dompetku.ui.LocalContainer
import id.dompetku.ui.components.CircleIcon
import id.dompetku.ui.components.Chip
import id.dompetku.ui.components.HSpace
import id.dompetku.ui.components.SectionCard
import id.dompetku.ui.components.categoryIcon
import id.dompetku.ui.theme.LocalDompetkuColors
import kotlinx.coroutines.launch

@Composable
fun CategoriesScreen(nav: NavController) {
    val c = LocalDompetkuColors.current
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    var type by remember { mutableStateOf("EXPENSE") }
    val list by remember(type) { container.categories.observe(type) }.collectAsState(initial = emptyList())
    var newName by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var subFor by remember { mutableStateOf<CategoryEntity?>(null) }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton({ nav.popBackStack() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, t("Kembali"), tint = c.text) }
            Text(t("Kategori"), style = MaterialTheme.typography.titleLarge, color = c.text)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(t("Pengeluaran"), type == "EXPENSE", { type = "EXPENSE" })
            Chip(t("Pemasukan"), type == "INCOME", { type = "INCOME" })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(newName, { newName = it }, label = { Text(t("Kategori baru")) }, singleLine = true, modifier = Modifier.weight(1f))
            HSpace(8)
            Button({
                if (newName.isNotBlank()) scope.launch {
                    container.categories.upsert(CategoryEntity(0, newName.trim(), type, "more", 0xFFC6F062, false))
                    newName = ""
                }
            }) { Text(t("Tambah")) }
        }
        message?.let { Text(it, color = c.expense, style = MaterialTheme.typography.bodySmall) }
        if (type == "EXPENSE") Text(t("Ketuk Kebutuhan/Keinginan/Tabungan untuk mengganti pos budget kategori."),
            style = MaterialTheme.typography.bodySmall, color = c.muted)
        SectionCard(Modifier.fillMaxWidth()) {
            list.filter { it.parentId == null }.forEach { cat ->
                CategoryRow(cat, indent = false, onAddSub = { subFor = cat },
                    onBucket = if (type == "EXPENSE") { b -> scope.launch { container.categories.setBucket(cat.id, b) } } else null,
                    onDelete = { scope.launch { container.categories.delete(cat.id).onFailure { message = it.message }.onSuccess { message = null } } })
                list.filter { it.parentId == cat.id }.forEach { sub ->
                    CategoryRow(sub, indent = true, onAddSub = null, onBucket = null,
                        onDelete = { scope.launch { container.categories.delete(sub.id).onFailure { message = it.message }.onSuccess { message = null } } })
                }
            }
        }
    }

    subFor?.let { parent ->
        var name by remember { mutableStateOf("") }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { subFor = null },
            title = { Text(t("Subkategori ") + parent.name) },
            text = { OutlinedTextField(name, { name = it }, label = { Text(t("Nama subkategori")) }, singleLine = true) },
            confirmButton = {
                androidx.compose.material3.TextButton({
                    if (name.isNotBlank()) scope.launch {
                        container.categories.upsert(CategoryEntity(0, name.trim(), parent.type, parent.icon, parent.color, false, null, parent.id))
                        subFor = null
                    }
                }) { Text(t("Tambah")) }
            },
            dismissButton = { androidx.compose.material3.TextButton({ subFor = null }) { Text(t("Batal")) } },
        )
    }
}

@Composable
private fun CategoryRow(
    cat: CategoryEntity, indent: Boolean, onAddSub: (() -> Unit)?,
    onBucket: ((id.dompetku.domain.Bucket) -> Unit)?, onDelete: () -> Unit,
) {
    val c = LocalDompetkuColors.current
    Row(Modifier.fillMaxWidth().padding(start = if (indent) 32.dp else 0.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        CircleIcon(categoryIcon(cat.icon), c.background, c.text, size = if (indent) 28 else 36)
        HSpace(12)
        Column(Modifier.weight(1f)) {
            Text(cat.name, style = MaterialTheme.typography.bodyLarge, color = c.text)
            if (onBucket != null) {
                val b = runCatching { id.dompetku.domain.Bucket.valueOf(cat.bucket ?: "WANTS") }.getOrDefault(id.dompetku.domain.Bucket.WANTS)
                val next = id.dompetku.domain.Bucket.entries[(b.ordinal + 1) % 3]
                Text(t(b.label), Modifier.padding(top = 2.dp).clickable { onBucket(next) },
                    style = MaterialTheme.typography.labelMedium, color = c.accent)
            }
        }
        if (onAddSub != null) androidx.compose.material3.TextButton(onAddSub) { Text(t("+ Sub")) }
        IconButton(onDelete) { Icon(Icons.Outlined.Delete, t("Hapus %s", cat.name), tint = c.muted) }
    }
}
