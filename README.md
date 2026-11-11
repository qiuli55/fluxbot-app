# FluxBot App

## 凭据与配置

- 后端访问令牌由用户在 App 的「连接服务器」面板中手动填写，不再内置在安装包里。
- 云桌面连接参数在构建时通过环境变量注入：`FLUXBOT_DESKTOP_HOST`、`FLUXBOT_DESKTOP_TOKEN`、`FLUXBOT_DESKTOP_BRIDGE`、`FLUXBOT_DESKTOP_BRIDGE_KEY`。未注入时，云桌面页面会提示未配置。
- 这些参数以前曾硬编码在源码中，仍保留在 git 历史里，必须在服务端轮换（重置）后才能视为安全。
