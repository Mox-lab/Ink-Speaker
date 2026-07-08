package com.ink.speaker.config.security;

import com.ink.speaker.auth.mapper.UserMapper;
import com.ink.speaker.auth.domain.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户详情加载服务。
 * <p>实现 Spring Security 的 UserDetailsService,从数据库读取用户与角色。</p>
 * <p>由 DaoAuthenticationProvider 在登录时调用。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserMapper userDao;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userDao.findByUsernameWithRoles(username)
                .orElseThrow(() -> {
                    log.debug("[Auth] 用户不存在: {}", username);
                    return new UsernameNotFoundException("用户不存在: " + username);
                });

        if (!user.isEnabled()) {
            log.debug("[Auth] 用户已被禁用: {}", username);
            throw new UsernameNotFoundException("用户已被禁用: " + username);
        }

        // 把 Role.name(如 ROLE_ADMIN)转为 SimpleGrantedAuthority
        Set<SimpleGrantedAuthority> authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(role.getName()))
                .collect(Collectors.toSet());

        log.debug("[Auth] 加载用户成功: {}, 角色: {}", username, authorities);
        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getUsername())
                .password(user.getPassword())
                .disabled(!user.isEnabled())
                .authorities(authorities)
                .build();
    }
}
