package com.sorts.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sorts.common.exception.BizException;
import com.sorts.common.result.ErrorCode;
import com.sorts.user.dto.PointsChangeRequest;
import com.sorts.user.dto.PointsVO;
import com.sorts.user.dto.UpdateUserRequest;
import com.sorts.user.dto.UserVO;
import com.sorts.user.entity.PointsLog;
import com.sorts.user.entity.User;
import com.sorts.user.mapper.PointsLogMapper;
import com.sorts.user.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用户服务单元测试：资料维护与积分账户。
 *
 * @author sorts
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserServiceImplTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private PointsLogMapper pointsLogMapper;

    @InjectMocks
    private UserServiceImpl userService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(7L);
        user.setUsername("weaver");
        user.setNickname("织梭者");
        user.setEmail("old@sorts.com");
        user.setPoints(50);
        user.setStatus(1);
        user.setDeleted(0);
    }

    @Test
    @DisplayName("查询资料：返回 VO 且不含密码字段")
    void getProfileShouldReturnVo() {
        when(userMapper.selectById(7L)).thenReturn(user);

        UserVO vo = userService.getProfile(7L);

        assertEquals(7L, vo.getId());
        assertEquals("织梭者", vo.getNickname());
        assertEquals(50, vo.getPoints());
    }

    @Test
    @DisplayName("查询不存在的用户抛出 NOT_FOUND")
    void getProfileShouldFailWhenUserMissing() {
        when(userMapper.selectById(404L)).thenReturn(null);
        BizException exception = assertThrows(BizException.class, () -> userService.getProfile(404L));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("更新资料：仅覆盖传入的非空字段")
    void updateProfileShouldUpdateOnlyProvidedFields() {
        when(userMapper.selectById(7L)).thenReturn(user);

        UpdateUserRequest request = new UpdateUserRequest();
        request.setNickname("新织梭者");

        UserVO vo = userService.updateProfile(7L, request);

        assertEquals("新织梭者", vo.getNickname());
        // 未传入的字段保持原值
        assertEquals("old@sorts.com", vo.getEmail());
        verify(userMapper).updateById(user);
    }

    @Test
    @DisplayName("积分查询：返回余额与最近流水")
    void getPointsShouldReturnBalanceAndLogs() {
        when(userMapper.selectById(7L)).thenReturn(user);

        PointsLog log = new PointsLog();
        log.setId(1L);
        log.setUserId(7L);
        log.setChangeAmount(5);
        log.setBalance(50);
        log.setReason("落梭奖励");
        when(pointsLogMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(log));

        PointsVO vo = userService.getPoints(7L, 10);

        assertEquals(50, vo.getPoints());
        assertEquals(1, vo.getLogs().size());
        assertEquals("落梭奖励", vo.getLogs().get(0).getReason());
    }

    @Test
    @DisplayName("增加积分：成功并写入流水")
    void changePointsShouldIncrease() {
        when(userMapper.selectById(7L)).thenReturn(user);
        when(userMapper.changePoints(7L, 10)).thenReturn(1);
        when(userMapper.selectPoints(7L)).thenReturn(60);

        PointsChangeRequest request = new PointsChangeRequest();
        request.setDelta(10);
        request.setReason("落梭奖励");
        request.setRelatedId(99L);

        int balance = userService.changePoints(7L, request);

        assertEquals(60, balance);
        verify(pointsLogMapper).insert(ArgumentMatchers.<PointsLog>any());
    }

    @Test
    @DisplayName("扣减积分余额不足：数据库条件更新返回 0，抛业务异常且不写流水")
    void changePointsShouldFailWhenInsufficient() {
        when(userMapper.selectById(7L)).thenReturn(user);
        // 模拟 where 条件不满足（余额不足）
        when(userMapper.changePoints(7L, -100)).thenReturn(0);

        PointsChangeRequest request = new PointsChangeRequest();
        request.setDelta(-100);
        request.setReason("锦市消费");

        BizException exception = assertThrows(BizException.class, () -> userService.changePoints(7L, request));
        assertEquals(ErrorCode.CONFLICT.getCode(), exception.getCode());
        assertEquals("光阴砂不足", exception.getMessage());
        verify(pointsLogMapper, never()).insert(ArgumentMatchers.<PointsLog>any());
    }

    @Test
    @DisplayName("变动值为 0 或空时拒绝处理")
    void changePointsShouldRejectZeroDelta() {
        PointsChangeRequest request = new PointsChangeRequest();
        request.setDelta(0);

        BizException exception = assertThrows(BizException.class, () -> userService.changePoints(7L, request));
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
        verify(userMapper, never()).changePoints(anyLong(), anyInt());
    }

    @Test
    @DisplayName("已注销用户（逻辑删除）不可变更积分")
    void changePointsShouldRejectDeletedUser() {
        user.setDeleted(1);
        when(userMapper.selectById(7L)).thenReturn(user);

        PointsChangeRequest request = new PointsChangeRequest();
        request.setDelta(5);

        assertThrows(BizException.class, () -> userService.changePoints(7L, request));
        verify(userMapper, never()).changePoints(anyLong(), anyInt());
    }
}
