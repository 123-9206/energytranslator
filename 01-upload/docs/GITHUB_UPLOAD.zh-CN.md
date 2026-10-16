# 上传到 GitHub 与本地启动

## 你拿到的是什么

这是完整能耗管理系统的**公开展示源码版本**，包含 Java 后端、Vue 管理后台和
微信小程序。它不是之前清单里的 Python/MCP Agent，不能按那份清单提交 MCP Registry。

已移除交接凭据、原线上地址与账号标识、内部开发/部署与审计材料、专利交底材料、
原商业获客网站及二维码。主要业务源码保留；具体修改和验证范围见
[公开版本修改说明](PUBLIC_RELEASE_NOTES.zh-CN.md)。

## 最简单的上传方法：GitHub Desktop

1. 解压 ZIP，进入 `energytranslator` 文件夹，确认里面直接有 `README.md`、
   `backend`、`admin`、`miniprogram` 等内容。
2. 使用你自己的 GitHub 账号登录 GitHub Desktop。
3. 选择 **File → Add local repository**，选择这个文件夹。如果提示不是 Git 仓库，
   点击创建仓库的链接，在此文件夹初始化；不要创建第二层同名空文件夹。
4. 查看 Changes。确认不包含 `.env`、密钥、客户账单、数据库备份。
   `.gitignore` 已配置好，但它不能自动移除以前已提交过的文件。
5. 提交本次文件，例如 `Prepare Energy Translator portfolio source`。
6. 选择 **Publish repository**，推荐名称 `energytranslator`。
   要公开展示时取消 **Keep this code private**，然后发布。

GitHub Desktop 的具体按钮位置可能随版本变化。不要将原始交接 ZIP 作为附件一起上传。
不要把仓库命名成 MCP Agent 并宣称已经实现 MCP 接口。

## 命令行方式

先在 GitHub 创建一个名为 `energytranslator` 的空仓库，不自动添加 README。
在解压后的 `energytranslator` 文件夹打开终端：

```bash
python scripts/leak_guard.py
git init -b main
git add .
python scripts/leak_guard.py
git diff --cached --stat
git commit -m "Prepare Energy Translator portfolio source"
git remote add origin https://github.com/YOUR_USERNAME/energytranslator.git
git push -u origin main
```

把 `YOUR_USERNAME` 换成你的实际 GitHub 用户名。提交作者使用你自己真实的 Git 配置，
不要把整理日期改成过去的开发日期，也不要用署名替代实际贡献记录。
这个交付 ZIP 不包含伪造的开发历史。

Description 可以使用：

`Energy-management application with a Spring Boot API, Vue dashboard and WeChat mini program.`

Topics 可以填写 `energy-management`、`spring-boot`、`vue`、`mongodb`、`iot`、
`portfolio`。本版本没有 MCP 接口，因此不建议添加 `mcp`。

## 本地运行

需要 Python 3.10+、Node.js 22.12+。后端可选择 Docker Compose，或 Java 17、Maven 3.9+
和单独运行的 MongoDB。Python 命令在部分电脑上叫 `python3`，Windows 也可用 `py`。

在项目根目录执行：

```bash
python scripts/init_local_env.py
```

该命令生成本地 `.env`，其中包含随机密钥及初始管理员密码。用本地编辑器查看即可；
不要提交、截图公开或发到 Issue。脚本不会覆盖已存在的 `.env`。

Docker 方式：

```bash
docker compose --env-file .env -f deploy/docker-compose.yml up --build
```

如果不用 Docker，先启动本机 MongoDB，再执行：

```bash
python scripts/run_backend.py
```

单独打开一个终端启动前端：

```bash
cd admin
npm ci --legacy-peer-deps
npm run dev
```

访问 `http://127.0.0.1:5173/admin/`，用 `.env` 中的初始管理员账号和密码登录。
第一次启动会创建管理员；以后启动不会重置已有用户。管理后台需要 `admin` 角色，
`demo` 企业账号用于小程序，不能代替后台管理员。

需要示例数据时，将根目录 `.env` 的 `SEED_ENABLED` 改为 `true` 并重启后端。
需要小程序“一键体验”时，再开启 `DEMO_LOGIN_ENABLED=true`。仅用于单独的本地演示数据库；
演示用户具有数据写入权限。真实设备采集和控制默认关闭，避免示例自动连接设备。

AI 读单需要你自行配置 `BILLCHECK_AI_API_KEY`，会把账单图片发送给配置的模型服务。
未配置时不能把模型识别功能视为已启用。请用虚构账单进行公开演示。

## 简历展示

先说明项目解决的问题，再写清你本人实际负责的模块、设计或测试。
可附仓库链接和使用合成数据录制的演示视频；不填写未经验证的节能率、客户数量或部署规模。
代码公开不代表服务已经在线，也不代表系统自动成为 MCP Agent。

本次整理没有替你决定开源许可；原包未包含项目 LICENSE，当前也没有新增 MIT/Apache 授权。
