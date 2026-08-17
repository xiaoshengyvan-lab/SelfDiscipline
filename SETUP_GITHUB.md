# 连接 GitHub 云构建 —— 你需要操作的 3 步

本机已把工程整理为可直接推送的 Git 仓库（含 GitHub Actions 云构建配置）。
HTTPS 在本环境被限制，但 SSH 可用，因此采用 **SSH 部署密钥** 方式，
由本机直接帮你推送代码并触发云端自动编译。

## 你需要操作的步骤（约 2 分钟）

### 第 1 步：创建 GitHub 仓库（一次）
- 打开 **https://github.com/new**
- Repository name 填：`SelfDiscipline`（可改，Public/Private 均可）
- 不要勾选 "Add a README" / ".gitignore"（避免冲突），直接 **Create repository**

### 第 2 步：添加部署公钥（一次）
- 打开刚创建的仓库 → **Settings → Deploy keys → Add deploy key**
- Title 随意填，如 `selfdiscipline-builder`
- Key 粘贴以下**公钥**（完整内容，以 `ssh-ed25519 AAAA...` 开头）：

```
<<< 公钥内容见聊天回复 / .github-deploy/id_ed25519.pub >>>
```

- **务必勾选 `Allow write access`** → Add key

### 第 3 步：把仓库地址告诉我
- 在仓库主页点 **Code** 按钮，复制 **SSH** 地址，形如：
  `git@github.com:你的用户名/SelfDiscipline.git`
- 直接回复给我即可。

## 之后我会自动完成

1. 推送代码到 `main` 分支 → GitHub Actions 自动编译（约 3~5 分钟）
2. 编译完成后给你 **Artifacts 下载链接**（登录 GitHub 即可下载 APK）
3. 需要正式发布时，我推送 `v1.0.0` 标签 → 自动生成 **Release 页面**，
   APK 挂在 Release 页面，**无需登录、可直接下载**（Public 仓库）

## 说明

- 私钥保存在本机 `.github-deploy/`（已加入 .gitignore，不会提交到仓库）
- 你随时可以删除该 Deploy key 撤销本机推送权限
- 每次改代码后告诉我，我会重新推送并触发新构建
