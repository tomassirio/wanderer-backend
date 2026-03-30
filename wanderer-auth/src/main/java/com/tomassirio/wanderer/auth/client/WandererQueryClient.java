package com.tomassirio.wanderer.auth.client;

import com.tomassirio.wanderer.commons.constants.ApiConstants;
import com.tomassirio.wanderer.commons.dto.UserBasicInfo;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "wanderer-query", url = "${wanderer.query.url}")
public interface WandererQueryClient {

    @GetMapping(ApiConstants.USERS_PATH + ApiConstants.USERNAME_ENDPOINT)
    UserBasicInfo getUserByUsername(
            @PathVariable String username, @RequestParam(value = "format") String format);

    @GetMapping(ApiConstants.USERS_PATH + ApiConstants.USER_BY_ID_ENDPOINT)
    UserBasicInfo getUserById(
            @PathVariable("id") UUID id, @RequestParam(value = "format") String format);
}
