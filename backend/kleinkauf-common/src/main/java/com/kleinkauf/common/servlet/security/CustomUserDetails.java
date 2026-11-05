package com.kleinkauf.common.servlet.security;

import com.kleinkauf.common.dto.RoleDto;
import com.kleinkauf.common.security.PermissionArea;
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

    // Authorities: ROLE_<role> for each role and PERM_<area> for the union of its roles' masks
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
