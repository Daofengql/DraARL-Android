package cn.silverdragon.draarl.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cn.silverdragon.draarl.tools.ChinaRegions
import cn.silverdragon.draarl.tools.RegionOption

@Composable
fun RegionPicker(value: String, onValueChange: (String) -> Unit) {
    val context = LocalContext.current
    val currentParts = value.split(' ').filter(String::isNotBlank)
    val selectedProvince = ChinaRegions.provinces.firstOrNull { it.name == currentParts.getOrNull(0) }
    val cities = remember(selectedProvince?.code) {
        selectedProvince?.let { ChinaRegions.cities(context, it.code) }.orEmpty()
    }
    val selectedCity = cities.firstOrNull { it.name == currentParts.getOrNull(1) }
    var choosingProvince by remember { mutableStateOf(false) }
    var choosingCity by remember { mutableStateOf(false) }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = { choosingProvince = true }, modifier = Modifier.weight(1f)) {
            Text(selectedProvince?.name ?: "选择省份", modifier = Modifier.weight(1f), maxLines = 1)
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        OutlinedButton(
            onClick = { choosingCity = true },
            enabled = selectedProvince != null,
            modifier = Modifier.weight(1f)
        ) {
            Text(selectedCity?.name ?: "选择城市", modifier = Modifier.weight(1f), maxLines = 1)
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
    }
    if (choosingProvince) {
        RegionChoiceDialog("选择省份", ChinaRegions.provinces, { choosingProvince = false }) { province ->
            onValueChange(province.name)
            choosingProvince = false
        }
    }
    if (choosingCity) {
        RegionChoiceDialog("选择城市", cities, { choosingCity = false }) { city ->
            onValueChange("${selectedProvince?.name.orEmpty()} ${city.name}".trim())
            choosingCity = false
        }
    }
}

@Composable
private fun RegionChoiceDialog(
    title: String,
    options: List<RegionOption>,
    onDismiss: () -> Unit,
    onSelect: (RegionOption) -> Unit
) {
    DraarlDialog(
        title = title,
        onDismissRequest = onDismiss,
        dismissAction = DraarlAction("取消", onDismiss)
    ) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 440.dp).padding(horizontal = 18.dp)) {
            items(options, key = RegionOption::code) { option ->
                Text(
                    option.name,
                    modifier = Modifier.fillMaxWidth().clickable { onSelect(option) }.padding(vertical = 14.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
                if (option != options.last()) HorizontalDivider()
            }
        }
    }
}
