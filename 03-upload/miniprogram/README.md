# 微信小程序客户端

使用微信开发者工具打开本目录。本公开版本将 AppID 替换为 `touristappid`，
实际开发、预览或上传时使用你自己的 AppID；部分微信能力需要正式注册的开发者账号。

- `utils/config.js` 默认 HTTP 模式，后端为 `http://127.0.0.1:8080`。
- 回环地址只适用于同机开发者工具；真机需要自己可访问的后端地址和合法 HTTPS 域名配置。
- 云环境标识和订阅消息模板 ID 已清空。云函数实现不在本仓库内。
- 后端默认关闭免密演示登录；仅对隔离演示数据库开启 `SEED_ENABLED=true`
  与 `DEMO_LOGIN_ENABLED=true` 后，才使用“一键体验”。
- 现有隐私/服务页面是原型文案，联系地址为示例。正式运营前按实际数据处理方式重写。

可选的 `upload.js` 必须显式提供 `WECHAT_APP_ID`、`WECHAT_PRIVATE_KEY_PATH` 和
`WECHAT_UPLOAD_VERSION`，并自行安装 `miniprogram-ci`。上传私钥放在仓库之外。
配置真实 AppID 后，上传前再次检查 Git diff；AppID 是公开标识，密钥必须保持私密。
