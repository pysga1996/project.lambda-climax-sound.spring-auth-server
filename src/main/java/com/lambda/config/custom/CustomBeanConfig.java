package com.lambda.config.custom;

import com.cloudinary.Cloudinary;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.StorageClient;
import com.lambda.error.FileStorageException;
import org.apache.catalina.Context;
import org.apache.catalina.connector.Connector;
import org.apache.coyote.http2.Http2Protocol;
import org.apache.tomcat.util.descriptor.web.SecurityCollection;
import org.apache.tomcat.util.descriptor.web.SecurityConstraint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnCloudPlatform;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.cloud.CloudPlatform;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.ErrorPage;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.server.ServletWebServerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.*;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.provider.token.ConsumerTokenServices;
import org.springframework.security.oauth2.provider.token.DefaultTokenServices;
import org.springframework.security.oauth2.provider.token.TokenStore;
import org.springframework.security.oauth2.provider.token.store.JdbcTokenStore;
import org.springframework.security.web.authentication.rememberme.JdbcTokenRepositoryImpl;
import org.springframework.security.web.authentication.rememberme.PersistentTokenBasedRememberMeServices;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.session.data.redis.config.ConfigureRedisAction;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

@Configuration
@DependsOn({"dataSource"})
@SuppressWarnings("deprecation")
public class CustomBeanConfig {

    private final DataSource dataSource;

    private final UserDetailsService userDetailsService;

    @Value("${storage.cloudinary.url}")
    private String cloudinaryUrl;

    @Value("${storage.firebase.database-url}")
    private String firebaseDatabaseUrl;

    @Value("${storage.firebase.storage-bucket}")
    private String firebaseStorageBucket;

    @Value("${storage.firebase.credentials}")
    private String firebaseCredentials;

    @Value("${spring.profiles.active:Default}")
    private String activeProfile;

    @Value("${custom.http-port}")
    private Integer httpPort;

    @Value("${custom.https-port}")
    private Integer httpsPort;

    @Value("${custom.security-policy}")
    private String securityPolicy;

    @Value("${custom.connector-scheme}")
    private String connectorScheme;

    @Autowired
    public CustomBeanConfig(DataSource dataSource,
                            @Lazy @Qualifier("userDaoImpl") UserDetailsService userDetailsService) {
        this.dataSource = dataSource;
        this.userDetailsService = userDetailsService;
    }

//    @Bean
//    public UserDetailsService userDetailsService() {
//        JdbcUserDetailsManager jdbcUserDetailsManager = new JdbcUserDetailsManager();
//        jdbcUserDetailsManager.setEnableAuthorities(false);
//        jdbcUserDetailsManager.setEnableGroups(true);
//        jdbcUserDetailsManager.setUsersByUsernameQuery("SELECT username, password, enabled,  FROM user WHERE username =?");
//        return jdbcUserDetailsManager;
//    }

    @Bean
    @Primary
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(4);
    }

    @Bean
    public MessageSource messageSource() {
        ReloadableResourceBundleMessageSource messageSource = new ReloadableResourceBundleMessageSource();
        messageSource.setBasename("classpath:static/i18n/message");
        messageSource.setDefaultEncoding("UTF-8");
        return messageSource;
    }

    @Bean
    public HandlerInterceptor localeChangeInterceptor() {
        LocaleChangeInterceptor lci = new LocaleChangeInterceptor();
        lci.setParamName("lang");
        return lci;
    }

    @Bean
    public TokenStore tokenStore() {
        return new JdbcTokenStore(dataSource);
    }

    @Bean
    @Primary
    public ConsumerTokenServices tokenServices() {
        DefaultTokenServices defaultTokenServices = new DefaultTokenServices();
        defaultTokenServices.setTokenStore(tokenStore());
        defaultTokenServices.setSupportRefreshToken(true);
        defaultTokenServices.setReuseRefreshToken(false);
        return defaultTokenServices;
    }

    @Bean
    public PersistentTokenRepository persistentTokenRepository() {
        JdbcTokenRepositoryImpl persistentTokenRepository = new JdbcTokenRepositoryImpl();
        persistentTokenRepository.setDataSource(dataSource);
        return persistentTokenRepository;
    }

    @Bean
    public PersistentTokenBasedRememberMeServices tokenBasedRememberMeServices() {
        return new PersistentTokenBasedRememberMeServices("lambda", userDetailsService, persistentTokenRepository());
    }

//    @Bean
//    public RememberMeAuthenticationFilter rememberMeAuthenticationFilter() {
//        return new RememberMeAuthenticationFilter(authenticationManager, tokenBasedRememberMeServices());
//    }
//
//    @Bean
//    public RememberMeAuthenticationProvider rememberMeAuthenticationProvider() {
//        return new RememberMeAuthenticationProvider("lambda");
//    }

    @Bean
    @Primary
    public JdbcOperations jdbcTemplate() {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    public TransactionOperations transactionOperations(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }

    @Bean
    @ConditionalOnProperty(prefix = "storage", name = "storage-type", havingValue = "cloudinary")
    public Cloudinary cloudinary() {
        return new Cloudinary(cloudinaryUrl);
    }

    @Bean
    @ConditionalOnProperty(prefix = "storage", name = "storage-type", havingValue = "firebase")
    public StorageClient firebaseStorage() {
        try {
            GoogleCredentials credentials = GoogleCredentials.fromStream(new ByteArrayInputStream(this.firebaseCredentials.getBytes()));
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(credentials)
                    .setDatabaseUrl(this.firebaseDatabaseUrl)
                    .setStorageBucket(this.firebaseStorageBucket)
                    .build();

            FirebaseApp fireApp = null;
            List<FirebaseApp> firebaseApps = FirebaseApp.getApps();
            if (firebaseApps != null && !firebaseApps.isEmpty()) {
                for (FirebaseApp app : firebaseApps) {
                    if (app.getName().equals(FirebaseApp.DEFAULT_APP_NAME))
                        fireApp = app;
                }
            } else
                fireApp = FirebaseApp.initializeApp(options);
            return StorageClient.getInstance(Objects.requireNonNull(fireApp));
        } catch (IOException ex) {
            throw new FileStorageException("Could not get admin-sdk json file. Please try again!", ex);
        }
    }

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> sessionManagerCustomizer() {
        return server -> {
            ErrorPage error400 = new ErrorPage(HttpStatus.BAD_REQUEST, "/error/400");
            ErrorPage error500 = new ErrorPage(HttpStatus.INTERNAL_SERVER_ERROR, "/error/500");
            ErrorPage error404 = new ErrorPage(HttpStatus.NOT_FOUND, "/error/404");
            ErrorPage error403 = new ErrorPage(HttpStatus.FORBIDDEN, "/error/403");
            ErrorPage error503 = new ErrorPage(HttpStatus.SERVICE_UNAVAILABLE, "/error/503");
            ErrorPage error401 = new ErrorPage(HttpStatus.UNAUTHORIZED, "/error/401");
            server.addErrorPages(error400, error500, error404, error403, error503
                    , error401
            );

        };
    }

    @Bean
    @Primary
    @ConditionalOnCloudPlatform(CloudPlatform.HEROKU)
    public ConfigureRedisAction configureRedisAction() {
        return ConfigureRedisAction.NO_OP;
    }

    @Bean
    @Primary
    public CookieSerializer cookieSerializer() {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName("SESSIONID");
        serializer.setCookiePath("/");
        switch (activeProfile) {
            case "heroku":
                serializer.setDomainName("lambda-auth-service.herokuapp.com");
                break;
            default:
                serializer.setDomainNamePattern("^.+?\\.(\\w+\\.[a-z]+)$");
        }
        return serializer;
    }

    @Bean
    @ConditionalOnCloudPlatform(CloudPlatform.NONE)
    public ServletWebServerFactory servletContainer() {
        TomcatServletWebServerFactory tomcat = new TomcatServletWebServerFactory() {
            @Override
            protected void postProcessContext(Context context) {
                SecurityConstraint securityConstraint = new SecurityConstraint();
                // set to CONFIDENTIAL to automatically redirect from http to https port
                securityConstraint.setUserConstraint(securityPolicy);
//                securityConstraint.setUserConstraint("NONE");
                SecurityCollection collection = new SecurityCollection();
                collection.addPattern("/*");
                securityConstraint.addCollection(collection);
                context.addConstraint(securityConstraint);
            }
        };
        tomcat.addAdditionalTomcatConnectors(getHttpConnector());
        return tomcat;
    }

    private Connector getHttpConnector() {
        Connector connector = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
        connector.setScheme(connectorScheme);
        connector.setPort(httpPort);
        connector.setSecure(false);
        connector.setRedirectPort(httpsPort);
        connector.addUpgradeProtocol(new Http2Protocol());
        return connector;
    }
}
