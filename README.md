# FluxBot App

## 凭据与配置

- 后端访问令牌由用户在 App 的「连接服务器」面板里用一次性配对码换取，只存在本机 Keystore 加密的 SecureStore 中，不内置在安装包里。
- 云桌面地址、profile 与短期票据全部由后端 `POST /api/v1/bots/{id}/desktop/session` 下发；App 不再接受任何构建期注入的地址或密钥（BuildConfig 里的字符串可以直接从 APK 读出）。
- 历史版本曾把桌面令牌硬编码在源码中，旧值仍留在 git 历史里，必须在服务端轮换（重置）后才能视为安全。
- Release 构建由 R8 压缩并混淆；每次发版请归档 `app/build/outputs/mapping/release/mapping.txt`，用于还原线上崩溃堆栈。
