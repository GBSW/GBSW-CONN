package kr.hs.gbsw.communication.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import kr.hs.gbsw.communication.common.response.ErrorResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI schoolCommunicationOpenApi() {
        Components components = new Components()
                .addSecuritySchemes("sessionCookie", new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.COOKIE)
                        .name("SESSION")
                        .description("로그인 후 서버가 발급하는 불투명 세션 쿠키"))
                .addSecuritySchemes("csrfHeader", new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name("X-XSRF-TOKEN")
                        .description("XSRF-TOKEN 쿠키 값을 상태 변경 요청에 전달"));
        ModelConverters.getInstance().read(ErrorResponse.class).forEach(components::addSchemas);

        return new OpenAPI()
                .info(new Info()
                        .title("학교 소통 제안 시스템 API")
                        .description("공개 제안, 정식 안건, 심의 및 재정·행정 공개를 위한 세션 기반 API")
                        .version("v1"))
                .components(components);
    }
}
