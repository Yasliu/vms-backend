package com.vms.config;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Fails startup with a clear message when a required database setting (for example DB_NAME or DB_PASSWORD)
 * is not defined. Without this, Spring would keep the literal "${DB_NAME}" text and fail later with an
 * unrelated JDBC/Hibernate error. Only the variable names are reported, never values.
 */
@Component
public class DatabaseConfigurationValidator implements BeanFactoryPostProcessor, EnvironmentAware {

    private static final Pattern PLACEHOLDER = Pattern.compile("Could not resolve placeholder '([^']+)'");
    private static final String[] KEYS = {
            "spring.datasource.url", "spring.datasource.username", "spring.datasource.password"
    };

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        Set<String> missing = missingVariables(environment);
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Missing required database configuration: " + String.join(", ", missing)
                    + ". Set them as environment variables or in a local .env file (see .env.example).");
        }
    }

    static Set<String> missingVariables(Environment environment) {
        Set<String> missing = new LinkedHashSet<>();
        for (String key : KEYS) {
            try {
                environment.getProperty(key);
            } catch (IllegalArgumentException ex) {
                Matcher m = PLACEHOLDER.matcher(String.valueOf(ex.getMessage()));
                missing.add(m.find() ? m.group(1) : key);
            }
        }
        return missing;
    }
}
