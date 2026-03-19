package cn.wanyj.auth.filter;

import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.*;

import java.net.InetSocketAddress;

/**
 * RPC Authentication Filter - Dubbo RPC 服务鉴权过滤器
 * 校验调用方 attachment 中的 rpc-service-token
 * 同时保存调用方 IP 到 ThreadLocal 供审计日志使用
 * @author wanyj
 */
@Slf4j
@Activate(group = CommonConstants.PROVIDER, order = -10000)
public class RpcAuthFilter implements Filter {

    private static volatile String serviceToken;

    /**
     * ThreadLocal 保存当前 RPC 调用的客户端 IP
     */
    private static final ThreadLocal<String> CLIENT_IP = new ThreadLocal<>();

    /**
     * Set service token from Spring managed config
     * 由 DubboFilterConfig 注入
     */
    public static void setServiceToken(String token) {
        RpcAuthFilter.serviceToken = token;
    }

    /**
     * 获取当前 RPC 调用的客户端 IP（供 AuditAspect 使用）
     */
    public static String getClientIp() {
        return CLIENT_IP.get();
    }

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        // 保存调用方 IP（在任何 early return 之前）
        String clientIp = resolveClientIp(invoker, invocation);
        if (clientIp != null) {
            CLIENT_IP.set(clientIp);
        }

        try {
            // If no token configured, skip authentication
            if (serviceToken == null || serviceToken.isBlank()) {
                return invoker.invoke(invocation);
            }

            String token = invocation.getAttachment("rpc-service-token");
            if (token == null || !token.equals(serviceToken)) {
                log.warn("RPC auth failed for method: {}", invocation.getMethodName());
                throw new RpcException(RpcException.FORBIDDEN_EXCEPTION, "RPC service token is invalid or missing");
            }

            return invoker.invoke(invocation);
        } finally {
            CLIENT_IP.remove();
        }
    }

    /**
     * 多种方式尝试获取调用方 IP
     */
    private String resolveClientIp(Invoker<?> invoker, Invocation invocation) {
        // 1. RpcContext.getServiceContext().getRemoteAddress()
        try {
            InetSocketAddress addr = RpcContext.getServiceContext().getRemoteAddress();
            if (addr != null && addr.getAddress() != null) {
                return addr.getAddress().getHostAddress();
            }
        } catch (Exception ignored) {
        }

        // 2. RpcContext 远程主机字符串
        try {
            String remoteHost = RpcContext.getServiceContext().getRemoteHost();
            if (remoteHost != null && !remoteHost.isEmpty() && !remoteHost.startsWith("0.")) {
                return remoteHost;
            }
        } catch (Exception ignored) {
        }

        // 3. 从 Dubbo attachment 中获取（调用方可以设置）
        try {
            String ip = invocation.getAttachment("client-ip")
                    != null ? invocation.getAttachment("client-ip") : invocation.getAttachment("client_ip");
            if (ip != null && !ip.isEmpty()) {
                return ip;
            }
        } catch (Exception ignored) {
        }

        // 4. 从 invoker URL 的 host 获取
        try {
            String host = invoker.getUrl().getHost();
            if (host != null && !host.isEmpty() && !host.startsWith("0.") && !"0:0:0:0:0:0:0:1".equals(host)) {
                return host;
            }
        } catch (Exception ignored) {
        }

        log.debug("Could not resolve RPC client IP for method: {}", invocation.getMethodName());
        return null;
    }
}
