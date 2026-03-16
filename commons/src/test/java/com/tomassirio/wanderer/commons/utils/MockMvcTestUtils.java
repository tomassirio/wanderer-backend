package com.tomassirio.wanderer.commons.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tomassirio.wanderer.commons.security.CurrentUserId;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.MethodParameter;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.data.web.config.SpringDataJacksonConfiguration;
import org.springframework.data.web.config.SpringDataWebSettings;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Test utility to build MockMvc instances with a CurrentUserId argument resolver. This class lives
 * in commons' test sources so other modules can reuse it via the commons test-jar.
 */
public class MockMvcTestUtils {

    public static MockMvc buildMockMvcWithCurrentUserResolver(
            Object controller, Object... controllerAdvice) {
        return buildMockMvcWithCurrentUserResolver(
                controller, BaseTestEntityFactory.USER_ID, controllerAdvice);
    }

    public static MockMvc buildMockMvcWithCurrentUserResolver(
            Object controller, UUID userId, Object... controllerAdvice) {
        return buildMockMvcWithCurrentUserResolver(controller, userId, null, controllerAdvice);
    }

    public static MockMvc buildMockMvcWithCurrentUserResolver(
            Object controller, UUID userId, ObjectMapper objectMapper, Object... controllerAdvice) {
        HandlerMethodArgumentResolver currentUserResolver =
                new HandlerMethodArgumentResolver() {
                    @Override
                    public boolean supportsParameter(MethodParameter parameter) {
                        return parameter.hasParameterAnnotation(CurrentUserId.class);
                    }

                    @Override
                    public Object resolveArgument(
                            @NotNull MethodParameter parameter,
                            ModelAndViewContainer mavContainer,
                            @NotNull NativeWebRequest webRequest,
                            WebDataBinderFactory binderFactory) {
                        return userId;
                    }
                };

        var builder = MockMvcBuilders.standaloneSetup(controller);
        if (controllerAdvice != null && controllerAdvice.length > 0) {
            builder = builder.setControllerAdvice(controllerAdvice);
        }
        builder =
                builder.setCustomArgumentResolvers(
                        currentUserResolver, new PageableHandlerMethodArgumentResolver());

        // Always configure a message converter so Page objects serialise correctly.
        // Spring Data's PageModule handles PageImpl serialisation (which otherwise
        // fails because Pageable.unpaged() throws on getPageNumber()/getPageSize()).
        if (objectMapper == null) {
            objectMapper = new ObjectMapper();
            objectMapper.registerModule(new JavaTimeModule());
        }
        objectMapper.registerModule(
                new SpringDataJacksonConfiguration.PageModule(
                        new SpringDataWebSettings(
                                EnableSpringDataWebSupport.PageSerializationMode.DIRECT)));
        MappingJackson2HttpMessageConverter messageConverter =
                new MappingJackson2HttpMessageConverter();
        messageConverter.setObjectMapper(objectMapper);
        builder = builder.setMessageConverters(messageConverter);

        return builder.build();
    }
}
