package com.kleinkauf.observability.audit;

import com.kleinkauf.observability.security.UsersClient;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;

/*
Name of whoever did each thing. It is looked up in users when the event is stored and saved with it: if the user
is deleted later, their audit still says who they were. Cached for 10 minutes so as not to ask on every
event. If users does not answer, the event is stored anyway without a name (the id remains).
 */
@Component
public class ActorNames {

    private final UsersClient usersClient;
    private final Cache<String, String> names = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofMinutes(10))
            .build();

    public ActorNames(UsersClient usersClient) {
        this.usersClient = usersClient;
    }

    public String name(Long userId, String tenant) {
        if (userId == null) {
            return null;
        }
        String key = tenant + ":" + userId;
        String cached = names.getIfPresent(key);
        if (cached != null) {
            return cached;
        }
        String name = usersClient.getUserName(userId, tenant);
        if (name != null) {
            names.put(key, name);
        }
        return name;
    }
}
