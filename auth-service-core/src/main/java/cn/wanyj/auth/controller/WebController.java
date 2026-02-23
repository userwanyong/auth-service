package cn.wanyj.auth.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Web Controller - Web页面控制器
 * 处理前端页面的路由
 * @author wanyj
 */
@Controller
@RequiredArgsConstructor
public class WebController {

    /**
     * Root path - redirect to dashboard (index.html)
     * Frontend JavaScript will handle authentication check and redirect to login if needed
     */
    @GetMapping("/")
    public String index() {
        return "redirect:/index.html";
    }

    /**
     * Login page
     */
    @GetMapping("/login")
    public String login() {
        return "redirect:/login.html";
    }
}
