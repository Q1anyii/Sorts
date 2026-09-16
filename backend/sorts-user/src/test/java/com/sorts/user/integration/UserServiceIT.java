package com.sorts.user.integration;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sorts.user.dto.LoginRequest;
import com.sorts.user.dto.PointsChangeRequest;
import com.sorts.user.dto.PointsVO;
import com.sorts.user.dto.RegisterRequest;
import com.sorts.user.dto.TokenVO;
import com.sorts.user.dto.UpdateUserRequest;
import com.sorts.user.dto.UserVO;
import com.sorts.user.entity.User;
import com.sorts.user.mapper.UserMapper;
import com.sorts.user.service.AuthService;
import com.sorts.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 用户服务集成测试：真实 MySQL 8 + 真实 Redis 7（Testcontainers）。
 *
 * <p>与 {@code *Test} 的区别：单测用 Mock 覆盖编排逻辑，这里跑的是**真实的
 * MyBatis SQL、事务与 Redis 读写**，能抓到单测抓不到的问题——
 * 例如 SQL 字段名写错、唯一索引冲突、Redis 序列化不兼容。</p>
 *
 * <p>运行方式（需要 Docker）：</p>
 * <pre>
 *   cd backend &amp;&amp; ./mvnw -Pintegration test -pl sorts-common,sorts-user
 * </pre>
 *
 * <p>没有 Docker 时整体跳过：{@code disabledWithoutDocker = true}，且 {@code *IT}
 * 只在 {@code -Pintegration} 下才被 surefire 收录，普通 {@code mvn test} 不受影响。</p>
 *
 * @author sorts
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class UserServiceIT {

    /**
     * 建表脚本直接复用仓库里的 DDL，避免另抄一份导致漂移。
     * 脚本含 CREATE DATABASE / USE，在已指定库的 JDBC 连接上可直接执行，且幂等。
     * 路径相对模块目录（backend/sorts-user）→ 仓库根 scripts/sql。
     */
    private static final String SCHEMA_SQL = "file:../../scripts/sql/sorts_user.sql";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("sorts_user")
            .withUsername("sorts")
            .withPassword("sorts_dev")
            .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @Autowired
    private AuthService authService;

    @Autowired
    private UserService userService;

    @Autowired
    private UserMapper userMapper;

    @DynamicPropertySource
    static void middlewareProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> MYSQL.getJdbcUrl()
                + "?useUnicode=true&characterEncoding=UTF-8&connectionCollation=utf8mb4_unicode_ci"
                + "&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true");
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
        registry.add("spring.sql.init.mode", () -> "always");
        registry.add("spring.sql.init.schema-locations", () -> SCHEMA_SQL);

        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));

        // 集成测试不接注册中心：Nacos 不是这里要验证的东西
        registry.add("spring.cloud.nacos.discovery.enabled", () -> "false");
        registry.add("spring.cloud.nacos.config.enabled", () -> "false");
    }

    @Test
    @DisplayName("注册：真实落库并签发双令牌，密码以 BCrypt 存储")
    void registerPersistsUser() {
        String username = "it_" + UUID.randomUUID().toString().substring(0, 8);

        TokenVO token = authService.register(registerRequest(username, "Sorts@2026", "集成测试用户"));

        assertNotNull(token.getAccessToken());
        assertNotNull(token.getRefreshToken());

        User saved = selectByUsername(username);
        assertEquals("集成测试用户", saved.getNickname());
        assertTrue(saved.getPassword().startsWith("$2"), "密码必须是 BCrypt 哈希，不能明文落库");
    }

    @Test
    @DisplayName("登录：真实库校验密码，错误口令被拒绝")
    void loginChecksRealPassword() {
        String username = "it_" + UUID.randomUUID().toString().substring(0, 8);
        authService.register(registerRequest(username, "Sorts@2026", null));

        TokenVO ok = authService.login(loginRequest(username, "Sorts@2026"));
        assertNotNull(ok.getAccessToken());

        assertThrows(Exception.class, () -> authService.login(loginRequest(username, "wrong-password")));
    }

    @Test
    @DisplayName("积分：真实事务下的增减与余额校验，余额不足不可为负")
    void pointsFlow() {
        String username = "it_" + UUID.randomUUID().toString().substring(0, 8);
        authService.register(registerRequest(username, "Sorts@2026", null));
        Long userId = selectByUsername(username).getId();

        userService.changePoints(userId, pointsRequest(100, "注册赠送"));
        assertEquals(100, userService.getPoints(userId, 10).getPoints());

        userService.changePoints(userId, pointsRequest(-30, "兑换装扮"));
        assertEquals(70, userService.getPoints(userId, 10).getPoints());

        assertThrows(Exception.class, () -> userService.changePoints(userId, pointsRequest(-999, "超额扣减")));
        assertEquals(70, userService.getPoints(userId, 10).getPoints(), "失败扣减后余额必须保持不变");
    }

    @Test
    @DisplayName("资料更新：只覆盖传入字段，用户名不变")
    void updateProfileKeepsOthers() {
        String username = "it_" + UUID.randomUUID().toString().substring(0, 8);
        authService.register(registerRequest(username, "Sorts@2026", "原名"));
        Long userId = selectByUsername(username).getId();

        UpdateUserRequest update = new UpdateUserRequest();
        update.setNickname("新昵称");
        UserVO updated = userService.updateProfile(userId, update);

        assertEquals("新昵称", updated.getNickname());
        assertEquals(username, updated.getUsername());
        assertNotEquals("原名", updated.getNickname());
    }

    // ---------------------------------------------------------------- 辅助

    private User selectByUsername(String username) {
        User saved = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        assertNotNull(saved, "应能在真实库里查到用户：" + username);
        return saved;
    }

    private static RegisterRequest registerRequest(String username, String password, String nickname) {
        RegisterRequest req = new RegisterRequest();
        req.setUsername(username);
        req.setPassword(password);
        req.setNickname(nickname);
        return req;
    }

    private static LoginRequest loginRequest(String username, String password) {
        LoginRequest req = new LoginRequest();
        req.setUsername(username);
        req.setPassword(password);
        return req;
    }

    private static PointsChangeRequest pointsRequest(int delta, String reason) {
        PointsChangeRequest req = new PointsChangeRequest();
        req.setDelta(delta);
        req.setReason(reason);
        return req;
    }
}
