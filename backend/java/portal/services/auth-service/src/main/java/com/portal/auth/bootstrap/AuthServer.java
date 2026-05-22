package com.portal.auth.bootstrap;

import com.portal.auth.adapter.in.http.AuthHttpHandler;
import com.portal.auth.adapter.in.http.MqttCommandConsumer;
import com.portal.auth.adapter.out.memory.InMemoryUserRepository;
import com.portal.auth.adapter.out.mqtt.MqttRustUserRepository;
import com.portal.auth.adapter.out.network.SshOpenWrtAccessGateway;
import com.portal.auth.adapter.out.notifications.MosquittoAsyncPublisher;
import com.portal.auth.adapter.out.notifications.Sha256PasswordHasher;
import com.portal.auth.adapter.out.notifications.SmtpSocketEmailSender;
import com.portal.auth.adapter.out.sqlite.SqliteCliUserRepository;
import com.portal.auth.application.port.out.AsyncEventPublisher;
import com.portal.auth.application.port.out.OpenWrtAccessGateway;
import com.portal.auth.application.port.out.UserRepository;
import com.portal.auth.application.service.AuthService;
import com.portal.auth.config.PortalConfig;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

public final class AuthServer {
    private AuthServer() {
    }

    public static void run(Path configPath) throws IOException {
        PortalConfig config = PortalConfig.fromToml(configPath);
        AsyncEventPublisher publisher = new MosquittoAsyncPublisher(config.mqttHost(), config.mqttPort());
        UserRepository repository = buildRepository(config);
        OpenWrtAccessGateway openWrtGateway = new SshOpenWrtAccessGateway(
                config.openWrtHost(),
                config.openWrtPort(),
                config.openWrtUser(),
                config.openWrtEnabled()
        );

        AuthService service = new AuthService(
                repository,
                new Sha256PasswordHasher(),
                publisher,
                new SmtpSocketEmailSender(config.smtpHost(), config.smtpPort(), config.smtpFrom()),
                openWrtGateway,
                config.sessionTtlSeconds()
        );

        MqttCommandConsumer commandConsumer = new MqttCommandConsumer(
                config.mqttHost(),
                config.mqttPort(),
                config.mqttTopicRegister(),
                config.mqttTopicLogin(),
                config.mqttTopicIssuePassword(),
                config.mqttTopicRegisterOut(),
                config.mqttTopicLoginOut(),
                config.mqttTopicIssuePasswordOut(),
                service,
                service,
                service,
                publisher
        );
        commandConsumer.start();

        Supplier<Boolean> dbMqttHealth = () -> !(repository instanceof MqttRustUserRepository r) || r.isHealthy();
        Supplier<String> portalToml = () -> {
            try {
                return Files.readString(configPath, StandardCharsets.UTF_8);
            } catch (IOException e) {
                return "";
            }
        };
        Supplier<String> portalIndexHtml = resourceSupplier("portal/index.html",
                "<!doctype html><html><body><div id='app'></div><script>window.__PORTAL_CONFIG__=__PORTAL_BOOTSTRAP_JSON__;</script><script type='module' src='/assets/portal-core.js'></script></body></html>");
        Supplier<String> portalCoreJs = resourceSupplier("portal/portal-core.js",
                "export function mountPortal(root){ root.innerHTML='<p>portal-core.js no disponible</p>'; }");
        Supplier<String> portalStylesCss = resourceSupplier("portal/styles.css",
                "body{font-family:sans-serif} #app{max-width:760px;margin:0 auto;padding:1rem}");

        HttpServer httpServer = HttpServer.create(new InetSocketAddress(config.httpPort()), 0);
        httpServer.createContext("/", new AuthHttpHandler(
                service,
                service,
                service,
                dbMqttHealth,
                portalToml,
                portalIndexHtml,
                portalCoreJs,
                portalStylesCss,
                config.registrationTemplate()
        ));
        httpServer.setExecutor(Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()));
        httpServer.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (repository instanceof MqttRustUserRepository r) {
                r.close();
            }
        }, "auth-shutdown-hook"));

        System.out.println("auth-service listening on :" + config.httpPort());
        System.out.println("auth-service repository=" + config.userRepositoryType());
    }

    private static UserRepository buildRepository(PortalConfig config) throws IOException {
        if ("memory".equalsIgnoreCase(config.userRepositoryType())) {
            return new InMemoryUserRepository();
        }
        if ("mqtt_rust".equalsIgnoreCase(config.userRepositoryType())) {
            return new MqttRustUserRepository(
                    config.mqttHost(),
                    config.mqttPort(),
                    config.dbMqttUserRequestTopic(),
                    config.dbMqttResponseWaitSeconds(),
                    config.dbMqttMaxRetries(),
                    config.dbMqttRetryBackoffMs()
            );
        }
        Path db = Path.of(config.sqliteDbPath());
        Path parent = db.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        return new SqliteCliUserRepository(db.toString());
    }

    private static Supplier<String> resourceSupplier(String resourcePath, String fallback) {
        return () -> {
            try (var in = AuthServer.class.getClassLoader().getResourceAsStream(resourcePath)) {
                if (in == null) {
                    return fallback;
                }
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                return fallback;
            }
        };
    }
}
