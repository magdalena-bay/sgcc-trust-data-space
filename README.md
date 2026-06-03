# sgcc-trust-data-space

面向能源可信数据空间的多方安全协同与隐私保护项目骨架仓库。

当前仓库只建立了工程框架，不包含正式业务代码。目标是先统一：

- 目录结构
- 模块边界
- 文档位置
- 部署入口
- Git 协作基础

## 目录结构

```text
sgcc-trust-data-space/
├─ docs/
│  ├─ architecture/
│  ├─ planning/
│  ├─ api/
│  └─ meeting-notes/
├─ frontend/
├─ backend/
│  ├─ platform-api/
│  └─ blockchain-service/
├─ services/
│  ├─ privacy-service/
│  ├─ fl-service/
│  └─ agent-service/
├─ contracts/
├─ deploy/
│  ├─ scripts/
│  └─ nginx/
├─ scripts/
├─ tests/
├─ data/
│  ├─ raw/
│  └─ processed/
└─ logs/
```

## 模块职责

- `frontend`：前端门户、审计页面、授权页面、可视化大屏、Agent 页面
- `backend/platform-api`：统一业务入口、任务编排、审计、权限与用户管理
- `backend/blockchain-service`：DID/VC、合约调用、中继链、链上审计
- `services/privacy-service`：AES、MA-CP-ABE、Paillier、撤销治理
- `services/fl-service`：FRLDRC、PFL-EPM、训练与推理接口
- `services/agent-service`：AI Agent 编排、报告生成、自然语言交互
- `contracts`：联盟链智能合约
- `docs`：方案、接口、架构、会议纪要、计划文档
- `deploy`：部署脚本、环境模板、网关配置

## 分支建议

- `main`：稳定演示版本
- `develop`：集成开发版本
- `feature/*`：个人或小组功能分支

## 下一步建议

1. 建立 GitHub 或 Gitee 私有仓库
2. 将本仓库推送到远程
3. 由各小组在对应模块下初始化各自工程
4. 优先打通最小主线闭环

## 最小主线闭环

`DID 注册 -> VC 签发 -> 数据加密上传 -> 链上存证 -> 授权访问 -> 撤销 -> 审计`
