package com.decor.common.servlet.security;

import com.decor.common.dto.RoleDto;
import com.decor.common.security.PermissionArea;
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

    // Authorities: ROLE_<rol> por cada rol y PERM_<area> por la union de las mascaras de sus roles
    public CustomUserDetails(Long id, List<RoleDto> roles) {
        this.id = id;
        int mask = 0;
        for  (RoleDto role : roles) {
            this.authorities.add(new SimpleGrantedAuthority("ROLE_"+role.getType()));
            mask |= role.getPermissions();
        }
        for (PermissionArea area : PermissionArea.fromMask(mask)) {
            this.authorities.add(new SimpleGrantedAuthority(area.authority()));
        }
    }

    public boolean hasPermission(PermissionArea area) {
        return authorities.stream().anyMatch(a -> area.authority().equals(a.getAuthority()));
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
