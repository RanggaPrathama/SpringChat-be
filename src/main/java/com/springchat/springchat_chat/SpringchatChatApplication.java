
package com.springchat.springchat_chat;
import java.util.Arrays;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SpringBootApplication
@EnableScheduling
public class SpringchatChatApplication {
	private static final Logger logger =
			LoggerFactory.getLogger(SpringchatChatApplication.class);

	public static void main(String[] args) {

		if (isProduction()) {
			System.setProperty(
					"spring.devtools.restart.enabled", "false");
		}

		ConfigurableApplicationContext context =
				SpringApplication.run(SpringchatChatApplication.class, args);

		checkHealth(context);

	}

	private static boolean isProduction() {
		String profiles = System.getenv().getOrDefault(
				"SPRING_PROFILES_ACTIVE",
				System.getProperty("spring.profiles.active", "")
		);

		return Arrays.stream(profiles.split(","))
				.map(String::trim)
				.anyMatch("prod"::equalsIgnoreCase);
	}

	public static void checkHealth(ConfigurableApplicationContext context){
		Environment env = context.getEnvironment();

		String appName = env.getProperty(
				"spring.application.name", "springchat-chat");

		String profile = String.join(",", env.getActiveProfiles());

		String level = env.getProperty("spring.profiles.active");

		if (profile.isBlank()) {
			profile = "default";
		}

		String host = env.getProperty(
				"server.address", "0.0.0.0");

		String contextPath = env.getProperty(
				"server.servlet.context-path", "");

		String protocol = Boolean.TRUE.equals(env.getProperty(
				"server.ssl.enabled", Boolean.class, false))
				? "https" : "http";

		int port = -1;

		if (context instanceof WebServerApplicationContext webContext) {
			port = webContext.getWebServer().getPort();
		}

		String localUrl = port >= 0
				? protocol + "://localhost:" + port + contextPath
				: "N/A";

		logger.info("""
                
                =============================================
                        SPRINGCHAT APPLICATION
                =============================================
                 Application : {}
                 Status      : RUNNING
                 Profile     : {}
                 Level       : {}
                 Host        : {}
                 Port        : {}
                 Local URL   : {}
                =============================================
                """,
				appName,
				profile,
				level,
				host,
				port >= 0 ? port : "N/A",
				localUrl
		);
	}
}
