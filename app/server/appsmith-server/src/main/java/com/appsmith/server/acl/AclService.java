package com.appsmith.server.acl;

import com.appsmith.server.configurations.AclConfig;
import com.appsmith.server.domains.User;
import com.appsmith.server.services.GroupService;
import com.appsmith.server.services.SessionUserService;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashSet;
import java.util.Set;

@Slf4j
@Component
public class AclService {

    private final String url;

    private final String pkgName;

    private final SessionUserService sessionUserService;
    private final GroupService groupService;

    @Autowired
    public AclService(
            AclConfig aclConfig,
            SessionUserService sessionUserService,
            GroupService groupService) {
        this.url = aclConfig.getHost();
        this.pkgName = aclConfig.getPkgName();
        this.sessionUserService = sessionUserService;
        this.groupService = groupService;
    }

    public Mono<OpaResponse> evaluateAcl(HttpMethod httpMethod, String resource) {
        JSONObject requestBody = new JSONObject();
        JSONObject input = new JSONObject();
        JSONObject jsonUser = new JSONObject();
        input.put("user", jsonUser);
        input.put("method", httpMethod.name());
        input.put("resource", resource);

        requestBody.put("input", input);

        Mono<User> user = sessionUserService.getCurrentUser();

        return user
                .map(u -> {
                    Set<String> globalPermissions = new HashSet<>();
                    Set<String> groupSet = u.getGroupIds();
                    globalPermissions.addAll(u.getPermissions());
                    return Flux.fromIterable(groupSet)
                            .flatMap(groupId ->
                                    groupService.getById(groupId)
                                            .map(group -> group.getPermissions())
                            ).map(obj -> globalPermissions.addAll(obj))
                            .collectList()
                            .thenReturn(globalPermissions);
                })
                .flatMap(obj -> obj)
                .flatMap(permissions -> {
                    jsonUser.put("permissions", permissions);
                    String finalUrl = url + pkgName;

                    log.debug("Going to make a data call to OPA: {} with data {}", url, requestBody);

                    WebClient webClient = WebClient.builder()
                            .baseUrl(finalUrl)
                            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .build();

                    WebClient.RequestHeadersSpec<?> request = webClient.post().syncBody(requestBody.toString());
                    return request.retrieve().bodyToMono(OpaResponse.class);
                });
    }
}
