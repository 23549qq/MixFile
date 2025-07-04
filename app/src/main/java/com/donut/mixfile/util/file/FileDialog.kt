package com.donut.mixfile.util.file

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.donut.mixfile.activity.video.VideoActivity
import com.donut.mixfile.app
import com.donut.mixfile.currentActivity
import com.donut.mixfile.server.core.objects.FileDataLog
import com.donut.mixfile.server.core.objects.isImage
import com.donut.mixfile.server.core.objects.isVideo
import com.donut.mixfile.server.core.utils.extensions.isTrue
import com.donut.mixfile.server.core.utils.hashSHA256
import com.donut.mixfile.server.core.utils.resolveMixShareInfo
import com.donut.mixfile.server.core.utils.shareCode
import com.donut.mixfile.server.core.utils.toHex
import com.donut.mixfile.ui.component.common.MixDialogBuilder
import com.donut.mixfile.ui.routes.favorites.openCategorySelect
import com.donut.mixfile.ui.routes.home.DownloadTask
import com.donut.mixfile.ui.routes.home.showDownloadTaskWindow
import com.donut.mixfile.ui.routes.useShortCode
import com.donut.mixfile.ui.routes.useSystemPlayer
import com.donut.mixfile.ui.theme.colorScheme
import com.donut.mixfile.util.CachedDelegate
import com.donut.mixfile.util.copyToClipboard
import com.donut.mixfile.util.formatFileSize
import com.donut.mixfile.util.showToast


@OptIn(ExperimentalLayoutApi::class)
fun showFileInfoDialog(
    dataLog: FileDataLog,
    onDismiss: () -> Unit = {}
) {
    var isFav = false

    val log by CachedDelegate({ arrayOf(favorites) }) {
        favorites.firstOrNull { it.isSimilar(dataLog).isTrue { isFav = true } } ?: dataLog
    }

    val shareInfo = resolveMixShareInfo(log.shareInfoData)
    if (shareInfo == null) {
        showToast("解析文件分享码失败")
        return
    }
    MixDialogBuilder("文件信息", tag = "file-info-${shareInfo.url}").apply {
        onDismiss(onDismiss)
        setNegativeButton("复制分享码") {
            shareInfo.shareCode(useShortCode).copyToClipboard()
        }
        setContent {
            val fileName = log.name
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.fillMaxWidth()
            ) {
                InfoText(key = "名称: ", value = fileName)
                InfoText(key = "大小: ", value = formatFileSize(shareInfo.fileSize))
                InfoText(key = "密钥: ", value = shareInfo.key)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (fileName.endsWith(".mix_list")) {
                        AssistChip(onClick = {
                            importFileList(log.downloadUrl)
                        }, label = {
                            Text(text = "文件列表", color = colorScheme.primary)
                        })
                    }
                    if (fileName.endsWith(".mix_dav")) {
                        AssistChip(onClick = {
                            previewWebDavData(log.downloadUrl)
                        }, label = {
                            Text(text = "查看文件", color = colorScheme.primary)
                        })
                    }
                    if (!isFav) {
                        AssistChip(onClick = {
                            addFavoriteLog(log)
                        }, label = {
                            Text(text = "收藏", color = colorScheme.primary)
                        })
                    } else {
                        AssistChip(onClick = {
                            deleteFavoriteLog(log)
                        }, label = {
                            Text(text = "取消收藏", color = colorScheme.primary)
                        })
                        AssistChip(onClick = {
                            log.rename {
                                closeDialog()
                                showFileInfoDialog(it, onDismiss)
                            }
                        }, label = {
                            Text(text = "重命名", color = colorScheme.primary)
                        })
                        AssistChip(onClick = {
                            openCategorySelect(log.getCategory()) { category ->
                                favorites = log.updateDataList(favorites) {
                                    log.copy(category = category)
                                }
                            }
                        }, label = {
                            Text(
                                text = "分类: ${log.getCategory()}",
                                color = colorScheme.primary
                            )
                        })

                    }

                    if (dataLog.isVideo) {
                        AssistChip(onClick = {
                            if (useSystemPlayer) {
                                val intent = Intent(Intent.ACTION_VIEW)
                                intent.setDataAndType(log.downloadUrl.toUri(), "video/*")
                                currentActivity.startActivity(intent)
                                return@AssistChip
                            }
                            val intent = Intent(app, VideoActivity::class.java).apply {
                                putExtra("url", log.downloadUrl)
                                putExtra("hash", shareInfo.toString().hashSHA256().toHex())
                            }
                            currentActivity.startActivity(intent)
                        }, label = {
                            Text(text = "播放视频", color = colorScheme.primary)
                        })
                    }
                    if (dataLog.isImage) {
                        AssistChip(onClick = {
                            showImageDialog(log.downloadUrl)
                        }, label = {
                            Text(text = "查看图片", color = colorScheme.primary)
                        })
                    }

                    AssistChip(onClick = {
                        log.lanUrl.copyToClipboard()
                    }, label = {
                        Text(text = "复制局域网地址", color = colorScheme.primary)
                    })
                }
            }
        }
        setPositiveButton("下载文件") {
            downloadFile(log)
            closeDialog()
            showDownloadTaskWindow()
        }
        show()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InfoText(key: String, value: String) {
    FlowRow {
        Text(text = key, fontSize = 14.sp, color = Color(117, 115, 115, 255))
        Text(
            text = value,
            color = colorScheme.primary.copy(alpha = 0.8f),
            textDecoration = TextDecoration.Underline,
            fontSize = 14.sp,
        )
    }
}

fun downloadFile(file: FileDataLog) {
    val task = DownloadTask(file.name, file.size, file.downloadUrl)
    task.start()
}
