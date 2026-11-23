package catdevs.georuraldatahub.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Garante que nenhum endpoint fique sem regra de perfil. */
class ControllerPermissionsTest {

    @Test
    void todoEndpointTemPreAuthorize() throws Exception {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        List<String> endpoints = new ArrayList<>();
        List<String> semRegra = new ArrayList<>();
        for (var candidate : scanner.findCandidateComponents("catdevs.georuraldatahub.controller")) {
            Class<?> type = ClassUtils.forName(candidate.getBeanClassName(), getClass().getClassLoader());
            for (Method method : type.getDeclaredMethods()) {
                if (AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class)) {
                    endpoints.add(type.getSimpleName() + "." + method.getName());
                    if (!method.isAnnotationPresent(PreAuthorize.class)
                            && !type.isAnnotationPresent(PreAuthorize.class)) {
                        semRegra.add(type.getSimpleName() + "." + method.getName());
                    }
                }
            }
        }

        assertThat(endpoints).isNotEmpty();
        assertThat(semRegra).as("Endpoints sem @PreAuthorize").isEmpty();
    }
}
