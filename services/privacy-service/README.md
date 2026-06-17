# privacy-service

`privacy-service` 是当前全链路 MVP 的 Python 侧隐私与证明服务。

它现在承担 4 类工作：

1. AES-GCM 数据正文加解密
2. `DEK` 包装与解包
3. `HD_i`、`policyHash`、`Package` 哈希生成
4. 演示版 Verkle 兼容证明生成与校验

说明：

- 当前代码为了先跑通全链路，采用了 `MVP_POLICY_WRAPPED_DEK` 作为 `CT_ABE` 的可运行占位实现。
- 当前证明层采用了 `Verkle-compatible demo commitment`，也就是保留 `HD_i -> ProofD_i -> Vroot` 的工程落点和验证流程，便于后续替换成真正的 Verkle / 多项式承诺后端。

## 主要接口

- `GET /health`
- `POST /api/privacy/encrypt-plaintext`
- `POST /api/privacy/package-ciphertext`
- `POST /api/privacy/commitments`
- `POST /api/privacy/verify`
- `POST /api/privacy/decrypt`

## Verkle-compatible demo commitment 测试

本模块已补充 `unittest` 测试，用来验证当前演示版 commitment 至少满足：

1. 同一批 `(data_id, HD_i)` 无论输入顺序如何，排序后得到稳定 root。
2. 正确 proof 能通过 root 校验。
3. 错误 value、错误 root、被篡改 sibling 都会校验失败。

运行方式：

```bash
cd services/privacy-service
python -m unittest discover -s tests -v
```

## Run

在服务器上：

```bash
cd ~/sgcc-trust-data-space/services/privacy-service
python3 -m uvicorn app.main:app --host 0.0.0.0 --port 8010
```

更推荐的服务器运行方式：

当前线上已经改成 `systemd --user` 托管，服务名为：

`privacy-service.service`

常用检查命令：

```bash
systemctl --user status privacy-service.service --no-pager -l
curl http://127.0.0.1:8010/health
```

如果这里不正常，后端的 `/api/demo/upload` 和 `/api/demo/access` 都可能直接返回 `500`。

可选环境变量：

- `SGCC_PRIVACY_MASTER_KEY_HEX`
  32 字节十六进制主密钥，用于包装 `DEK`
