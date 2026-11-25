package com.perm.admin.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.net.URI;

/**
 * 服务就绪后自动打开浏览器，仅在 perm.openBrowser=true 时生效
 * （打包成 exe 的 jpackage 参数 -Dperm.openBrowser=true 打开此开关）。
 */
@Component
public class BrowserLauncher implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BrowserLauncher.class);

    private final Environment environment;

    public BrowserLauncher(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!Boolean.parseBoolean(environment.getProperty("perm.openBrowser", "false"))) {
            return;
        }
        Integer port = environment.getProperty("local.server.port", Integer.class,
                environment.getProperty("server.port", Integer.class, 18917));
        String url = "http://localhost:" + port;
        if (GraphicsEnvironment.isHeadless() || !Desktop.isDesktopSupported()) {
            log.warn("当前环境不支持自动打开浏览器，请手动访问 {}", url);
            return;
        }
        try {
            Desktop.getDesktop().browse(URI.create(url));
            log.info("已自动打开浏览器: {}", url);
        } catch (Exception e) {
            log.warn("自动打开浏览器失败: {} - 请手动访问 {}", url, e.getMessage());
        }
    }
}
