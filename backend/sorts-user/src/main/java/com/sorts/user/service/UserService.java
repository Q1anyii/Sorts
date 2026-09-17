package com.sorts.user.service;

import com.sorts.user.dto.PointsChangeRequest;
import com.sorts.user.dto.PointsVO;
import com.sorts.user.dto.UpdateUserRequest;
import com.sorts.user.dto.UserVO;
import org.springframework.core.io.FileSystemResource;
import org.springframework.web.multipart.MultipartFile;

/**
 * 用户服务：资料维护与积分账户。
 *
 * @author sorts
 */
public interface UserService {

    /** 查询用户资料 */
    UserVO getProfile(Long userId);

    /** 更新用户资料（仅更新传入的非空字段） */
    UserVO updateProfile(Long userId, UpdateUserRequest request);

    /** 上传并更换头像：落盘 + 更新 avatar_url（返回更新后的用户资料） */
    UserVO uploadAvatar(Long userId, MultipartFile file);

    /** 读取头像静态文件（不存在返回 null） */
    FileSystemResource loadAvatarFile(String filename);

    /** 查询积分余额与最近流水 */
    PointsVO getPoints(Long userId, int limit);

    /** 积分变动：正增负减，余额不足时抛业务异常 */
    int changePoints(Long userId, PointsChangeRequest request);
}
