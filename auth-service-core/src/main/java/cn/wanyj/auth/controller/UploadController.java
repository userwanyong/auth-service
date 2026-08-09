package cn.wanyj.auth.controller;

import cn.wanyj.auth.exception.ApiResponse;
import cn.wanyj.auth.security.SecurityUtils;
import cn.wanyj.auth.service.OssService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 文件上传控制器（头像等）
 *
 * @author wanyj
 */
@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
public class UploadController {

    private final OssService ossService;

    /**
     * 上传头像（需登录）
     * POST /api/upload/avatar
     */
    @PostMapping("/avatar")
    public ResponseEntity<ApiResponse<Map<String, String>>> uploadAvatar(
            @RequestParam("file") MultipartFile file) {
        Long userId = SecurityUtils.getCurrentUserId();
        Long tenantId = SecurityUtils.getCurrentTenantId();
        String url = ossService.uploadAvatar(file, tenantId, userId);
        return ResponseEntity.ok(ApiResponse.success(200, "上传成功", Map.of("url", url)));
    }
}
