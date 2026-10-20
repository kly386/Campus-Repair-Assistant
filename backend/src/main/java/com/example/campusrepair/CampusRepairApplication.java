package com.example.campusrepair;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

/**
 * 应用启动入口
 *
 * @Description 校园快修服务助手应用启动类（支持可执行 JAR/WAR 与外部 Tomcat 部署）
 * @Author 开璐瑶
 * @Date 2026-09-22
 * @Version 1.0
 */
@SpringBootApplication
public class CampusRepairApplication extends SpringBootServletInitializer {

	public static void main(String[] args) {
		SpringApplication.run(CampusRepairApplication.class, args);
	}

	/**
	 * 部署到外部 Servlet 容器（Tomcat 11）时的入口。
	 *
	 * @param application Spring 应用构建器
	 * @return 注册了本应用配置类的构建器
	 */
	@Override
	protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
		return application.sources(CampusRepairApplication.class);
	}

}
