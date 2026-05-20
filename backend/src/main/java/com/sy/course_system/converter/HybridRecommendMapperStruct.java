package com.sy.course_system.converter;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.NullValueMappingStrategy;

import com.sy.course_system.dto.recommend.HybridRecommendItemDTO;
import com.sy.course_system.dto.recommend.HybridRecommendResponseDTO;
import com.sy.course_system.vo.HybridRecommendItemVO;
import com.sy.course_system.vo.HybridRecommendResponseVO;

/**
 * 推荐接口出参映射器。
 *
 * 该映射器承担“内部 DTO -> 前端 VO”的边界收口职责：
 * - service 内部仍可保留 finalScore/cfScore 等实现细节；
 * - recommendSource 作为验收推荐链路的稳定字段透出；
 * - controller 对外只返回经过裁剪的稳定 VO 结构；
 * - 推荐链路里知识点相关结构已统一复用 VO，本映射器只需要收口响应层与推荐项本身。
 */
@Mapper(componentModel = "spring", nullValueMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
public interface HybridRecommendMapperStruct {
    HybridRecommendItemVO toItemVO(HybridRecommendItemDTO dto);

    List<HybridRecommendItemVO> toItemVOList(List<HybridRecommendItemDTO> dtos);

    /**
     * 响应层只保留 items，不再透出 userId。
     */
    default HybridRecommendResponseVO toResponseVO(HybridRecommendResponseDTO dto) {
        HybridRecommendResponseVO vo = new HybridRecommendResponseVO();
        vo.setItems(dto == null || dto.getItems() == null ? List.of() : toItemVOList(dto.getItems()));
        return vo;
    }
}
