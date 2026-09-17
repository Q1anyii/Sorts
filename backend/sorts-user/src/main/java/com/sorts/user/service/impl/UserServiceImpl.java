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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;
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

    /** 头像存储目录（容器内由 sorts-user-avatar 卷持久化） */
    @Value("${sorts.user.avatar-dir:./data/avatar}")
    private String avatarDir;

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

    /** 允许的图片类型与单文件上限 */
    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/png", "image/jpeg", "image/gif", "image/webp");
    private static final long MAX_AVATAR_BYTES = 2L * 1024 * 1024; // 2MB
    /** 头像 URL 前缀（网关已放行免鉴权，img 标签可直读） */
    private static final String AVATAR_URL_PREFIX = "/api/v1/users/avatar/files/";

    @Override
    public UserVO uploadAvatar(Long userId, MultipartFile file) {
        User user = requireUser(userId);
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "请选择要上传的头像图片");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new BizException(ErrorCode.PARAM_ERROR, "仅支持 PNG / JPG / GIF / WebP 格式的头像");
        }
        if (file.getSize() > MAX_AVATAR_BYTES) {
            throw new BizException(ErrorCode.PARAM_ERROR, "头像图片不能超过 2MB");
        }
        String ext = extensionOf(contentType);
        String filename = "u" + userId + "_" + System.currentTimeMillis() + "." + ext;
        try {
            Path dir = Paths.get(avatarDir).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            Path target = dir.resolve(filename).normalize();
            if (!target.startsWith(dir)) {
                throw new BizException(ErrorCode.PARAM_ERROR, "非法文件名");
            }
            file.transferTo(target.toFile());
        } catch (IOException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "头像保存失败，请稍后再试");
        }
        // 清理旧头像文件（仅删除本站托管的历史文件，失败不阻塞主流程）
        String old = user.getAvatarUrl();
        if (old != null && old.startsWith(AVATAR_URL_PREFIX)) {
            String oldName = old.substring(AVATAR_URL_PREFIX.length()).split("[?]")[0];
            if (oldName.matches("[A-Za-z0-9._-]+")) {
                try { Files.deleteIfExists(Paths.get(avatarDir).resolve(oldName)); } catch (IOException ignored) { }
            }
        }
        String avatarUrl = AVATAR_URL_PREFIX + filename;
        user.setAvatarUrl(avatarUrl);
        userMapper.updateById(user);
        return UserVO.from(user);
    }

    @Override
    public FileSystemResource loadAvatarFile(String filename) {
        if (filename == null || !filename.matches("[A-Za-z0-9._-]+")) {
            return null;
        }
        Path base = Paths.get(avatarDir).toAbsolutePath().normalize();
        Path file = base.resolve(filename).normalize();
        if (!file.startsWith(base)) {
            return null;
        }
        return new FileSystemResource(file);
    }

    /** 由 Content-Type 推导扩展名（白名单内，避免用户提供的文件名/扩展名） */
    private String extensionOf(String contentType) {
        switch (contentType.toLowerCase(Locale.ROOT)) {
            case "image/png": return "png";
            case "image/jpeg": return "jpg";
            case "image/gif": return "gif";
            default: return "webp";
        }
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
