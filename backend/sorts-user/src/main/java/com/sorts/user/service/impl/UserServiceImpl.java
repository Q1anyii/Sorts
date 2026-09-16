package com.sorts.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sorts.common.exception.BizException;
import com.sorts.common.result.ErrorCode;
import com.sorts.user.dto.PointsChangeRequest;
import com.sorts.user.dto.PointsLogVO;
import com.sorts.user.dto.PointsVO;
import com.sorts.user.dto.UpdateUserRequest;
import com.sorts.user.dto.UserVO;
import com.sorts.user.entity.PointsLog;
import com.sorts.user.entity.User;
import com.sorts.user.mapper.PointsLogMapper;
import com.sorts.user.mapper.UserMapper;
import com.sorts.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户服务实现：资料维护与积分账户。
 *
 * @author sorts
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    /** 积分明细默认返回条数 */
    private static final int DEFAULT_LOG_LIMIT = 20;

    private final UserMapper userMapper;

    private final PointsLogMapper pointsLogMapper;

    @Override
    public UserVO getProfile(Long userId) {
        return UserVO.from(requireUser(userId));
    }

    @Override
    public UserVO updateProfile(Long userId, UpdateUserRequest request) {
        User user = requireUser(userId);
        if (StringUtils.hasText(request.getNickname())) {
            user.setNickname(request.getNickname());
        }
        if (StringUtils.hasText(request.getEmail())) {
            user.setEmail(request.getEmail());
        }
        if (StringUtils.hasText(request.getPhone())) {
            user.setPhone(request.getPhone());
        }
        if (StringUtils.hasText(request.getAvatarUrl())) {
            user.setAvatarUrl(request.getAvatarUrl());
        }
        userMapper.updateById(user);
        return UserVO.from(user);
    }

    @Override
    public PointsVO getPoints(Long userId, int limit) {
        User user = requireUser(userId);
        int size = limit <= 0 ? DEFAULT_LOG_LIMIT : Math.min(limit, 100);

        List<PointsLog> logs = pointsLogMapper.selectList(new LambdaQueryWrapper<PointsLog>()
                .eq(PointsLog::getUserId, userId)
                .orderByDesc(PointsLog::getCreatedAt)
                .last("LIMIT " + size));

        List<PointsLogVO> logVOs = logs.stream().map(log -> {
            PointsLogVO vo = new PointsLogVO();
            vo.setId(log.getId());
            vo.setChangeAmount(log.getChangeAmount());
            vo.setBalance(log.getBalance());
            vo.setReason(log.getReason());
            vo.setRelatedId(log.getRelatedId());
            vo.setCreatedAt(log.getCreatedAt());
            return vo;
        }).toList();

        return PointsVO.builder().points(user.getPoints()).logs(logVOs).build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int changePoints(Long userId, PointsChangeRequest request) {
        if (request == null || request.getDelta() == null || request.getDelta() == 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "积分变动值非法");
        }
        User user = requireUser(userId);
        int delta = request.getDelta();

        // 条件更新由数据库保证不会扣成负数，返回 0 表示余额不足
        int rows = userMapper.changePoints(userId, delta);
        if (rows == 0) {
            throw new BizException(ErrorCode.CONFLICT, delta < 0 ? "光阴砂不足" : "积分变动失败");
        }

        Integer balance = userMapper.selectPoints(userId);
        PointsLog log = new PointsLog();
        log.setUserId(userId);
        log.setChangeAmount(delta);
        log.setBalance(balance);
        log.setReason(request.getReason());
        log.setRelatedId(request.getRelatedId());
        log.setCreatedAt(LocalDateTime.now());
        pointsLogMapper.insert(log);

        // 同步内存中的余额，保证同一事务内再次读取一致
        user.setPoints(balance);
        return balance;
    }

    private User requireUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null || Integer.valueOf(1).equals(user.getDeleted())) {
            throw new BizException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        return user;
    }
}
