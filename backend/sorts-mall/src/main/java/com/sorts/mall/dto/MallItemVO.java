package com.sorts.mall.dto;

import com.sorts.mall.entity.MallItem;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品视图对象（字段与 api-spec.json 的 MallItem 对齐）。
 *
 * @author sorts
 */
@Data
public class MallItemVO {

    private Long id;

    private String name;

    private String type;

    private String description;

    private String imageUrl;

    private String previewUrl;

    private Integer price;

    private Integer stock;

    private String status;

    private LocalDateTime createdAt;

    public static MallItemVO from(MallItem item) {
        if (item == null) {
            return null;
        }
        MallItemVO vo = new MallItemVO();
        vo.setId(item.getId());
        vo.setName(item.getName());
        vo.setType(item.getType());
        vo.setDescription(item.getDescription());
        vo.setImageUrl(item.getImageUrl());
        vo.setPreviewUrl(item.getPreviewUrl());
        vo.setPrice(item.getPrice());
        vo.setStock(item.getStock());
        vo.setStatus(item.getStatus());
        vo.setCreatedAt(item.getCreatedAt());
        return vo;
    }
}
