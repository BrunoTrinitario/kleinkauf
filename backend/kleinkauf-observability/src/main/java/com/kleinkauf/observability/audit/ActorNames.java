package com.kleinkauf.observability.audit;

import com.kleinkauf.observability.security.UsersClient;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;

/*
Nombre de quien hizo cada cosa. Se busca en users al guardar el evento y queda guardado con el: si despues
borran al usuario, su auditoria sigue diciendo quien era. Se guarda 10 minutos para no preguntar por cada
evento. Si users no responde, el evento se guarda igual sin nombre (queda el id).
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
