# Single8 Ports And Layout

更新时间：`2026-06-26`

## 1. 目标

为后续单机单链 `8` 节点部署预留独立目录：

`/home/ubuntu/blockchain/fisco/single8`

## 2. 目录规划

建议远端结构：

```text
/home/ubuntu/blockchain/fisco/single8/
  generated/
    127.0.0.1/
      node0/
      node1/
      node2/
      node3/
      node4/
      node5/
      node6/
      node7/
    start_all.sh
    stop_all.sh
  webase-single8/
```

## 3. 端口规划

建议连续端口段如下：

| Node | P2P Port | RPC Port |
|---|---:|---:|
| node0 | 30380 | 21000 |
| node1 | 30381 | 21001 |
| node2 | 30382 | 21002 |
| node3 | 30383 | 21003 |
| node4 | 30384 | 21004 |
| node5 | 30385 | 21005 |
| node6 | 30386 | 21006 |
| node7 | 30387 | 21007 |

WeBASE 建议端口：

1. 主入口：
   `5110`
2. 如需备用入口：
   `5111`

## 4. 约束

1. 不覆盖当前：
   `qingdao / weifang / relay`
2. 不默认开机自启
3. 不与当前：
   `20200/20400/20600`
   冲突
4. 不与当前：
   `5100/5101/5102`
   冲突
