# FluxBot App

## 凭据与配置

- 后端访问令牌由用户在 App 的「连接服务器」面板里用一次性配对码换取，只存在本机 Keystore 加密的 SecureStore 中，不内置在安装包里。
- 云桌面入口先由 `GET /api/v1/desktop` 下发：`configured` 是服务端判定的权威标志，另有 `gateway`、`bridge`、`token`、`bridge_key`、`config_url`、`tunnel_updated_at`。ticket 模式下 `configured` 可以为 true，而 `token` 与 `bridge_key` 是 JSON null（客户端解析为空串），不能以凭据为空反推未配置。
- 打开某个 Bot 的桌面时，再调用 `POST /api/v1/bots/{id}/desktop/session` 换取该 Bot 的短期会话（`host`、`bridge`、`profile`、`ticket`、`expires_at`，可选 `gateway_token`、`bridge_key`）；服务端返回 503 `desktop_not_configured` 时按未配置处理。
- 连接凭据优先使用短期 `ticket`，没有时才退回长期 `gateway_token`。App 不再接受任何构建期注入的桌面地址或密钥（BuildConfig 里的字符串可以直接从 APK 读出）。
- 历史版本曾把桌面令牌硬编码在源码中，旧值仍留在 git 历史里，必须在服务端轮换（重置）后才能视为安全。
- Release 构建由 R8 压缩并混淆；每次发版请归档 `app/build/outputs/mapping/release/mapping.txt`，用于还原线上崩溃堆栈。
