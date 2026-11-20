package com.vms.config;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Refuses to start without a strong signing secret. This matters because Spring would otherwise keep
 * an unresolved "${JWT_SECRET}" as literal text, which would then silently become a publicly known
 * signing key. The secret value is never included in any message.
 */
@Component
public class JwtConfigurationValidator implements BeanFactoryPostProcessor, EnvironmentAware {

    static final int MIN_SECRET_BYTES = 32;
    private static final String KEY = "app.security.jwt.secret";
    private static final Pattern PLACEHOLDER = Pattern.compile("Could not resolve placeholder '([^']+)'");

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        String problem = problemWith(environment);
        if (problem != null) {
            throw new IllegalStateException(problem + " Set JWT_SECRET (for example the output of "
                    + "`openssl rand -base64 48`) as an environment variable or in the local .env file.");
        }
    }

    static String problemWith(Environment environment) {
        String secret;
        try {
            secret = environment.getProperty(KEY);
        } catch (IllegalArgumentException ex) {
            Matcher m = PLACEHOLDER.matcher(String.valueOf(ex.getMessage()));
            return "Missing required security configuration: " + (m.find() ? m.group(1) : KEY) + ".";
        }
        if (secret == null || secret.isBlank()) {
            return "Missing required security configuration: JWT_SECRET.";
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            return "JWT_SECRET is too short: it must be at least " + MIN_SECRET_BYTES + " characters.";
        }
        return null;
    }
}
