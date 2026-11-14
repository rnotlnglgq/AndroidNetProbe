# 自有 HTTPS 出口对照服务

可选的 Python 3.10+ 标准库服务。客户端也可使用内置 Cloudflare 检查，无需部署此服务。

## 部署

准备两个由自己管理的域名及可信 TLS 证书：IPv4 域名只配置 A，IPv6 域名只配置 AAAA。以下路径和域名均为占位符，证书与私钥不要放进仓库。

```bash
python3 server/probe_server.py --family IPv4 --bind 0.0.0.0 --port 8443 \
  --certfile /secure/v4/fullchain.pem --keyfile /secure/v4/privkey.pem
python3 server/probe_server.py --family IPv6 --bind :: --port 8443 \
  --certfile /secure/v6/fullchain.pem --keyfile /secure/v6/privkey.pem
```

在应用中填写对应的 `https://<your-domain>:8443/probe`。IPv6 服务设置 `IPV6_V6ONLY=1`。

## 响应

```json
{"observedIp":"192.0.2.1","ipFamily":"IPv4","serverTimeUtc":"2026-11-14T00:00:00Z","requestId":"random-uuid"}
```

`observedIp` 只取自 TCP 对端，不信任 `X-Forwarded-For` 或 `Forwarded`。若经普通 HTTP 反向代理转发，返回的会是代理地址；此模板不实现可信代理链恢复。服务不写访问日志、不保存请求，适合个人低并发实验。

客户端仅发送 GET，不上传快照；使用系统 TLS 校验。两次请求绑定开始时的 App 默认 Network，不随切换自动换网。自有模式要求 DNS 的可见结果符合指定地址族；DNS64、VPN、代理等仍可能影响结果解释。响应最多 16 KiB，不支持压缩，不跟随重定向。

## 本地测试

```bash
python3 -m unittest discover -s server -v
```

测试覆盖响应格式及真实回环 TCP 请求中伪造代理头无效，不等于公网 TLS 或双栈部署验证。
