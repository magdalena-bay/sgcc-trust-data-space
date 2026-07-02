# Vite Runtime And Secret Baseline

## 1. Secrets

从现在开始，以下值不再允许以内置默认值方式存在于仓库：

- `SGCC_MYSQL_USERNAME`
- `SGCC_MYSQL_PASSWORD`
- `SGCC_REDIS_PASSWORD`
- `SGCC_POSTGRES_USERNAME`
- `SGCC_POSTGRES_PASSWORD`
- `SGCC_CHAIN_SERVICE_USER_NAME`
- `SGCC_CHAIN_SERVICE_PRIVATE_KEY`
- `SGCC_CHAIN_SERVICE_ADDRESS`
- `SGCC_PRIVACY_MASTER_KEY_HEX`

推荐流程：

1. 复制 `.server.env.example` 为服务器上的 `.server.env`
2. 为每项敏感值填入真实环境值
3. 对已经暴露过的密码和私钥执行轮换
4. 重启 `privacy-service`、`platform-api` 与依赖组件

生成 32 字节十六进制密钥示例：

```bash
openssl rand -hex 32
```

## 2. Vite 常驻运行的结论

这次需要防的不是 “Vite 不能运行”，而是下面这类错误用法：

1. 把 `npm run dev` 写成长期 `systemd --user` 服务
2. 配 `Restart=always`
3. 带 `--force` 反复重建依赖缓存
4. 让 `vite/esbuild` 在旧路径、坏路径或重复路径上自我复活

允许的做法是：

1. 只在需要远程前端联调时启动
2. 使用受控 transient unit，而不是永久自启 unit
3. 给 Node 堆上限、内存上限、任务数上限
4. 默认只监听 `127.0.0.1`
5. 通过 Nginx 反向代理到服务器主 URL

## 3. 推荐运行模式

### 调试模式

适合需要 HMR 的短时排查：

```bash
cd /home/ubuntu/sgcc-trust-data-space
./scripts/ops/start_frontend_server.sh --mode dev
```

### 预览模式

适合“从 SSH 启动前端并直接访问服务器网址”，但不需要 HMR：

```bash
cd /home/ubuntu/sgcc-trust-data-space
./scripts/ops/start_frontend_server.sh --mode preview
```

停止：

```bash
cd /home/ubuntu/sgcc-trust-data-space
./scripts/ops/stop_frontend_server.sh all
```

## 4. 直接访问服务器网址

如果希望浏览器直接访问服务器主 URL，而不是只看 `8089`：

1. 启动 `platform-api:8088`
2. 启动前端 `dev` 或 `preview`
3. 使用 `deploy/nginx/sgcc-direct-vite.conf`
4. 让 Nginx 代理：
   - `/` -> `127.0.0.1:5173` 或 `127.0.0.1:4173`
   - `/api/` -> `127.0.0.1:8088/api/`

如果改用 preview，请把 Nginx 里的 `5173` 改成 `4173`。
