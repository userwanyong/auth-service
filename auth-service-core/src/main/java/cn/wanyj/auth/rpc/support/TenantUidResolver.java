package cn.wanyj.auth.rpc.support;

import cn.wanyj.auth.entity.Tenant;
import cn.wanyj.auth.exception.BusinessException;
import cn.wanyj.auth.exception.ErrorCode;
import cn.wanyj.auth.service.TenantService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * RPC 层租户标识解析器：对外 tenantUid → 内部数字 tenantId
 * <p>RPC 入参统一只认 tenantUid；数字 tenantId 不对外暴露。
 * 解析失败（不存在/禁用/过期）抛 {@link ErrorCode#INVALID_TENANT}，
 * 与登录、验证码等既有流程的租户校验行为一致。</p>
 *
 * @author wanyj
 */
@Component
@RequiredArgsConstructor
public class TenantUidResolver {

    private final TenantService tenantService;

    /**
     * 严格解析：tenantUid 必须对应一个有效租户（存在且未禁用/未过期）
     *
     * @param tenantUid 对外租户标识
     * @return 租户实体
     * @throws BusinessException INVALID_TENANT，当标识为空、租户不存在或已失效
     */
    public Tenant requireTenant(String tenantUid) {
        Tenant tenant = tenantService.getTenantByUid(tenantUid);
        if (tenant == null || !tenant.isValid()) {
            throw new BusinessException(ErrorCode.INVALID_TENANT);
        }
        return tenant;
    }

    /**
     * 宽松解析：tenantUid 留空返回 null（跳过归属校验语义），非空则严格校验
     *
     * @param tenantUid 对外租户标识，可为空
     * @return 租户实体；标识为空时返回 null
     * @throws BusinessException INVALID_TENANT，当标识非空但租户不存在或已失效
     */
    public Tenant resolveTenantOrNull(String tenantUid) {
        if (tenantUid == null || tenantUid.isBlank()) {
            return null;
        }
        return requireTenant(tenantUid);
    }
}
