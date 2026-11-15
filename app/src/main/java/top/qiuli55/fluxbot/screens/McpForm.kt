package top.qiuli55.fluxbot.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.qiuli55.fluxbot.data.validateMcpForm
import top.qiuli55.fluxbot.ui.FIcon
import top.qiuli55.fluxbot.ui.Flux
import top.qiuli55.fluxbot.ui.FluxInput
import top.qiuli55.fluxbot.ui.PrimaryButton

/** 表单里要提交给服务器的四个字段。 */
data class McpFormValue(
    val name: String,
    val url: String,
    val headerName: String,
    val headerValue: String,
)

/**
 * 「自定义 MCP」的添加表单。
 *
 * 纯展示组件：输入状态和校验都在内部完成，提交时把清洗后的值交给调用方；
 * 网络错误通过 [error] 传进来，显示在按钮上方而不是用 Toast 遮住。
 */
@Composable
fun McpAddForm(
    busy: Boolean,
    error: String,
    onSubmit: (McpFormValue) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var headerName by remember { mutableStateOf("") }
    var headerValue by remember { mutableStateOf("") }
    var headerOpen by remember { mutableStateOf(false) }
    var showValue by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf("") }
    val message = localError.ifBlank { error }

    FormLabel("名称", top = 4)
    FluxInput(name, { name = it; localError = "" }, placeholder = "例如：公司知识库")

    FormLabel("地址")
    FluxInput(
        url,
        { url = it; localError = "" },
        placeholder = "https://mcp.example.com/sse",
        keyboardType = KeyboardType.Uri,
    )

    Row(
        Modifier.fillMaxWidth()
            .clickable { headerOpen = !headerOpen }
            .padding(top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("鉴权头（可选）", fontSize = 11.5.sp, color = Flux.text3, modifier = Modifier.weight(1f))
        FIcon(
            if (headerOpen) FIcon.ChevronDown else FIcon.ChevronRight,
            size = 14.dp,
            color = Flux.text4,
        )
    }
    if (headerOpen) {
        FluxInput(headerName, { headerName = it; localError = "" }, placeholder = "Authorization")
        Spacer(Modifier.height(8.dp))
        FluxInput(
            headerValue,
            { headerValue = it; localError = "" },
            placeholder = "Bearer …",
            password = !showValue,
            trailing = {
                Box(
                    Modifier.size(30.dp).clip(CircleShape).background(Flux.panel3)
                        .clickable { showValue = !showValue },
                    contentAlignment = Alignment.Center,
                ) {
                    FIcon(FIcon.Eye, size = 14.dp, color = Flux.text2)
                }
            },
        )
        Text(
            "值只随这次请求发给服务器，不会保存在手机上。",
            fontSize = 11.sp,
            color = Flux.text4,
            lineHeight = 17.sp,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp),
        )
    }

    if (message.isNotBlank()) {
        Text(
            message,
            fontSize = 12.sp,
            color = Flux.danger,
            lineHeight = 18.sp,
            modifier = Modifier.padding(start = 4.dp, top = 12.dp),
        )
    }

    Spacer(Modifier.height(16.dp))
    PrimaryButton(
        if (busy) "正在连接并检查工具…" else "连接",
        enabled = !busy,
    ) {
        val value = McpFormValue(name.trim(), url.trim(), headerName.trim(), headerValue)
        val problem = validateMcpForm(name, url, headerName, headerValue)
        if (problem != null) {
            localError = problem
        } else {
            localError = ""
            onSubmit(value)
        }
    }
    Text(
        "连接后，Bot 可以在任务里调用这个服务提供的工具。",
        fontSize = 11.sp,
        color = Flux.text4,
        lineHeight = 17.sp,
        modifier = Modifier.padding(start = 4.dp, top = 10.dp, bottom = 2.dp),
    )
}

@Composable
private fun FormLabel(text: String, top: Int = 14) {
    Text(
        text,
        fontSize = 11.5.sp,
        color = Flux.text3,
        modifier = Modifier.padding(start = 4.dp, top = top.dp, bottom = 7.dp),
    )
}
