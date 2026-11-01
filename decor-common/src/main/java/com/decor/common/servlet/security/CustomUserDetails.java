package com.decor.common.servlet.security;

import com.decor.common.dto.RoleDto;
import lombok.Getter;
import lombok.Setter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Getter
@Setter
public class CustomUserDetails implements UserDetails {

    private Long id;
    private Collection<GrantedAuthority> authorities = new ArrayList<>();

    public CustomUserDetails(Long id, List<RoleDto> roles) {
        this.id = id;
        for  (RoleDto role : roles) {
            this.authorities.add(new SimpleGrantedAuthority("ROLE_"+role.getType()));
        }
    }

    @Override
    public String getPassword() {
        return "";
    }

    @Override
    public String getUsername() {
        return "";
    }
}
