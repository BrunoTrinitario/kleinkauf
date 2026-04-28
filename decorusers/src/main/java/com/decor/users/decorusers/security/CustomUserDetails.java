package com.decor.users.decorusers.security;

import java.util.*;

import com.decor.users.decorusers.services.UserService;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.decor.users.decorusers.entities.Role;
import com.decor.users.decorusers.entities.User;

public class CustomUserDetails implements UserDetails {

    private final User user;

    private ArrayList<GrantedAuthority> authorities = new ArrayList<>();

    public CustomUserDetails(User user, Set<String> roles) {
        this.user = user;
        for (String r : roles) {
            authorities.add(new SimpleGrantedAuthority("ROLE_"+r));
        }
    }

    public User getUser() {
        return user;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return this.authorities;
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    public String getUsername() {
        return user.getEmail();
    }
}
