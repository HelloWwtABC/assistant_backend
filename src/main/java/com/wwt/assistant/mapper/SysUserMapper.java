package com.wwt.assistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wwt.assistant.entity.SysUser;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    @Select("""
            SELECT id, team_id, username, password_hash, name, email, role, status,
                   last_login_at, created_at, updated_at, deleted_at, deleted_by
            FROM sys_user
            WHERE username = #{username}
              AND deleted_at IS NULL
            LIMIT 1
            """)
    SysUser findActiveByUsername(@Param("username") String username);

    @Update("""
            UPDATE sys_user
            SET last_login_at = #{loginTime},
                updated_at = #{loginTime}
            WHERE id = #{userId}
              AND deleted_at IS NULL
            """)
    int updateLoginSuccess(@Param("userId") Long userId, @Param("loginTime") LocalDateTime loginTime);
}
