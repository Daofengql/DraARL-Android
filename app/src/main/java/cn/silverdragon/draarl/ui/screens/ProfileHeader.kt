package cn.silverdragon.draarl.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.silverdragon.draarl.data.User
import cn.silverdragon.draarl.data.formatRadioIdentifiers
import cn.silverdragon.draarl.ui.components.CommandIconButton
import cn.silverdragon.draarl.ui.components.StatusIndicator
import cn.silverdragon.draarl.ui.components.StatusTone
import cn.silverdragon.draarl.ui.components.UserAvatar
import cn.silverdragon.draarl.ui.theme.appColors
import cn.silverdragon.draarl.ui.theme.dataTypography

@Composable
internal fun ProfileHeader(
    user: User,
    onAvatarClick: () -> Unit,
    onEditClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onPresetsClick: () -> Unit
) {
    val radioIdentifiers = formatRadioIdentifiers(user.mdcId, user.dmrId)
    Column(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(80.dp).clickable(onClick = onAvatarClick),
                            contentAlignment = Alignment.BottomEnd
                        ) {
                            UserAvatar(url = user.avatarUrl, modifier = Modifier.size(80.dp))
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                shadowElevation = 2.dp
                            ) {
                                Icon(
                                    Icons.Default.CameraAlt,
                                    contentDescription = "更换头像",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(5.dp).size(16.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                user.displayName,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                listOfNotNull(
                                    "@${user.username}",
                                    user.callsign.takeIf(String::isNotBlank)
                                ).joinToString("  "),
                                style = MaterialTheme.dataTypography.value,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            StatusIndicator(
                                text = when (user.approvalStatus) {
                                    1 -> "已审核"
                                    2 -> "未通过"
                                    else -> "待审核"
                                },
                                tone = when (user.approvalStatus) {
                                    1 -> StatusTone.CONNECTED
                                    2 -> StatusTone.ERROR
                                    else -> StatusTone.CONNECTING
                                }
                            )
                        }
                    }
                    if (radioIdentifiers.isNotBlank()) {
                        ProfileMetaRow(
                            icon = Icons.Default.Badge,
                            text = radioIdentifiers,
                            technical = true
                        )
                    }
                    if (user.address.isNotBlank()) {
                        ProfileMetaRow(
                            icon = Icons.Default.LocationOn,
                            text = user.address
                        )
                    }
                }
                Row(
                    modifier = Modifier.padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CommandIconButton(onClick = onEditClick, contentDescription = "编辑资料", icon = Icons.Default.Edit)
                    CommandIconButton(
                        onClick = onPresetsClick,
                        contentDescription = "电台预设",
                        icon = Icons.Default.FavoriteBorder
                    )
                    CommandIconButton(onClick = onSettingsClick, contentDescription = "设置", icon = Icons.Default.Settings)
                }
            }
            if (user.introduction.isNotBlank()) {
                Text(
                    user.introduction,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!user.isApproved) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            if (user.approvalStatus == 2) "账号审核未通过" else "账号正在等待审核",
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            user.reviewNote.ifBlank { "审核通过后可使用在线收发、设备、群组和通联日志。" },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.appColors.divider)
            }
        }
    }
}

@Composable
private fun ProfileMetaRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    technical: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            style = if (technical) MaterialTheme.dataTypography.value else MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
