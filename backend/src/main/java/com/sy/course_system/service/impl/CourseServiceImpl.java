package com.sy.course_system.service.impl;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sy.course_system.common.PageResult;
import com.sy.course_system.common.UserContext;
import com.sy.course_system.converter.CourseMapperStruct;
import com.sy.course_system.dto.course.CoursePageLearnerCountDTO;
import com.sy.course_system.dto.course.CoursePageTagDTO;
import com.sy.course_system.dto.course.CourseRegisterOptionsDTO;
import com.sy.course_system.dto.course.CourseQueryDTO;
import com.sy.course_system.dto.course.CourseRegisterDTO;
import com.sy.course_system.dto.course.CourseTempDTO;
import com.sy.course_system.dto.course.CourseUpdateDTO;
import com.sy.course_system.dto.course.KnowledgePointOptionDTO;
import com.sy.course_system.dto.course.TagOptionDTO;
import com.sy.course_system.entity.Course;
import com.sy.course_system.entity.Knowledge;
import com.sy.course_system.entity.Tag;
import com.sy.course_system.enums.CourseStatus;
import com.sy.course_system.mapper.CourseHotScoreMapper;
import com.sy.course_system.mapper.CourseMapper;
import com.sy.course_system.mapper.KnowledgePointMapper;
import com.sy.course_system.mapper.TagMapper;
import com.sy.course_system.repository.CourseNodeRepository;
import com.sy.course_system.service.CourseService;
import com.sy.course_system.service.CourseTagService;
import com.sy.course_system.service.LearningAnalysisService;
import com.sy.course_system.service.VideoService;
import com.sy.course_system.vo.CourseDetailVO;
import com.sy.course_system.vo.CourseUpdateVO;
import com.sy.course_system.vo.CourseVO;
import com.sy.course_system.vo.KnowledgeVO;

@Service
public class CourseServiceImpl extends ServiceImpl<CourseMapper, Course> implements CourseService {
    private static final Logger log = LoggerFactory.getLogger(CourseServiceImpl.class);

    private final CourseMapperStruct courseMapperStruct;
    private final CourseNodeRepository courseNodeRepository;
    private final LearningAnalysisService learningAnalysisService;
    private final VideoService videoService;
    private final CourseTagService courseTagService;
    private final TagMapper tagMapper;
    private final KnowledgePointMapper knowledgePointMapper;
    private final CourseHotScoreMapper courseHotScoreMapper;

    public CourseServiceImpl(CourseMapperStruct courseMapperStruct,
            CourseNodeRepository courseNodeRepository,
            LearningAnalysisService learningAnalysisService,
            VideoService videoService,
            CourseTagService courseTagService,
            TagMapper tagMapper,
            KnowledgePointMapper knowledgePointMapper,
            CourseHotScoreMapper courseHotScoreMapper) {
        this.courseMapperStruct = courseMapperStruct;
        this.courseNodeRepository = courseNodeRepository;
        this.learningAnalysisService = learningAnalysisService;
        this.videoService = videoService;
        this.courseTagService = courseTagService;
        this.tagMapper = tagMapper;
        this.knowledgePointMapper = knowledgePointMapper;
        this.courseHotScoreMapper = courseHotScoreMapper;
    }

    @Override
    public CourseRegisterOptionsDTO getRegisterOptions() {
        List<TagOptionDTO> tags = tagMapper.listEnabledTagOptions();
        List<KnowledgePointOptionDTO> knowledgePoints = knowledgePointMapper.listEnabledKnowledgePointOptions();

        CourseRegisterOptionsDTO dto = new CourseRegisterOptionsDTO();
        dto.setTags(tags);
        dto.setKnowledgePoints(knowledgePoints);
        return dto;
    }

    // ===== 前台课程池 =====
    @Override
    public PageResult<CourseVO> pageForUser(CourseQueryDTO dto) {
        // 1.分页参数
        int page = dto.getPage() != null && dto.getPage() > 0 ? dto.getPage() : 1;
        int pageSize = dto.getPageSize() != null && dto.getPageSize() > 0 ? dto.getPageSize() : 9;

        // 2.构建分页对象
        Page<CourseTempDTO> pageParam = new Page<>(page, pageSize, false);

        // 3.执行分页查询
        Page<CourseTempDTO> coursePage = baseMapper.selectCoursePage(pageParam, UserContext.getUserId(), dto);
        Long total = baseMapper.selectCoursePageCount(dto);
        coursePage.setTotal(total);

        // 4.当前页批量回补展示聚合字段
        List<CourseTempDTO> courseTempList = coursePage.getRecords();
        enrichCoursePageRecords(courseTempList);

        // 5.转换为VO
        List<CourseVO> courses = convertToVO(courseTempList);

        // 6.返回分页结果
        return PageResult.of(coursePage.getTotal(), page, pageSize, courses);

    }

    private void enrichCoursePageRecords(List<CourseTempDTO> courseTempList) {
        if (courseTempList == null || courseTempList.isEmpty()) {
            return;
        }

        List<Long> courseIds = courseTempList.stream()
                .map(CourseTempDTO::getId)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());
        if (courseIds.isEmpty()) {
            return;
        }

        Map<Long, Integer> learnerMap = loadCoursePageLearnerMap(courseIds);
        Map<Long, String> tagMap = loadCoursePageTagMap(courseIds);
        for (CourseTempDTO course : courseTempList) {
            Long courseId = course.getId();
            course.setLearners(learnerMap.getOrDefault(courseId, 0));
            course.setTags(tagMap.get(courseId));
        }
    }

    private Map<Long, Integer> loadCoursePageLearnerMap(List<Long> courseIds) {
        Map<Long, Integer> result = new HashMap<>();
        List<CoursePageLearnerCountDTO> rows = baseMapper.selectCoursePageLearnerCounts(courseIds);
        if (rows == null || rows.isEmpty()) {
            return result;
        }
        for (CoursePageLearnerCountDTO row : rows) {
            if (row != null && row.getCourseId() != null) {
                result.put(row.getCourseId(), row.getLearners() == null ? 0 : row.getLearners());
            }
        }
        return result;
    }

    private Map<Long, String> loadCoursePageTagMap(List<Long> courseIds) {
        Map<Long, String> result = new HashMap<>();
        List<CoursePageTagDTO> rows = baseMapper.selectCoursePageTags(courseIds);
        if (rows == null || rows.isEmpty()) {
            return result;
        }
        for (CoursePageTagDTO row : rows) {
            if (row != null && row.getCourseId() != null) {
                result.put(row.getCourseId(), row.getTags());
            }
        }
        return result;
    }

    private List<CourseVO> convertToVO(List<CourseTempDTO> courseTempList) {
        return courseTempList.stream()
                .map(courseMapperStruct::tempToVO)
                .collect(Collectors.toList());
    }

    @Override
    public List<Long> getKnowledgePointIdsByCourseId(Long courseId) {
        return baseMapper.selectKnowledgePointIdsByCourseId(courseId);
    }

    @Override
    public List<CourseDetailVO> getCourseDetailsByKnowledgePointId(Long kpId) {
        List<Course> courses = baseMapper.selectCoursesByKnowledgePointId(kpId);
        if (courses == null || courses.isEmpty()) {
            return null;
        }
        return courseMapperStruct.toDetailVOs(courses);
    }

    // ===== 后台课程管理 =====

    /**
     * 课程注册
     * 
     * @param registerDTO 注册信息，包含课程的相关信息
     * @return 返回注册结果，成功返回提示信息，失败返回对应错误信息
     */
    @Override
    @Transactional(transactionManager = "transactionManager")
    public Long register(CourseRegisterDTO registerDTO) {
        QueryWrapper<Course> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("title", registerDTO.getTitle());

        if (baseMapper.selectOne(queryWrapper) != null) {
            return -1L; // 课程已存在
        }

        if (registerDTO.getCategoryId() == null || registerDTO.getTagIds() == null || registerDTO.getTagIds().isEmpty()
                || registerDTO.getKnowledgePointIds() == null || registerDTO.getKnowledgePointIds().isEmpty()) {
            throw new IllegalArgumentException("categoryId or tagIds or kpIds is empty");
        }

        Map<Integer, Tag> tagMap = courseTagService.getTagMapByIds(registerDTO.getTagIds());
        if (tagMap == null || tagMap.isEmpty()) {
            throw new IllegalArgumentException("Invalid tagIds");
        }

        Course course = courseMapperStruct.toEntity(registerDTO);
        course.setStatus(CourseStatus.DRAFT.getCode()); // 默认草稿状态,待上传完视频后再上线

        this.save(course);

        // 关联课程分类
        baseMapper.insertCourseCategoryRelations(course.getId(),
                Collections.singletonList(registerDTO.getCategoryId()));
        // 关联课程标签
        baseMapper.insertCourseTagRelations(course.getId(), tagMap);
        // 关联课程知识点
        baseMapper.insertCourseKnowledgePointRelations(course.getId(), registerDTO.getKnowledgePointIds());
        // 创建课程知识图谱根节点
        courseNodeRepository.createCourse(course.getId(), course.getTitle());
        // 知识图谱关联课程知识点
        courseNodeRepository.bindKnowledgePoints(course.getId(), registerDTO.getKnowledgePointIds());

        return course.getId(); // 注册成功，返回课程ID
    }

    @Override
    @Transactional(transactionManager = "transactionManager")
    public boolean update(CourseUpdateDTO updateDTO) {
        if (updateDTO == null || updateDTO.getId() == null) {
            throw new IllegalArgumentException("courseId is required");
        }
        if (updateDTO.getCategoryId() == null
                || updateDTO.getTagIds() == null || updateDTO.getTagIds().isEmpty()
                || updateDTO.getKnowledgePointIds() == null || updateDTO.getKnowledgePointIds().isEmpty()) {
            throw new IllegalArgumentException("categoryId or tagIds or kpIds is empty");
        }

        Course course = this.getById(updateDTO.getId());
        if (course == null) {
            return false;
        }

        // 1. 更新课程基本信息
        if (updateDTO.getTitle() != null && !updateDTO.getTitle().isBlank()) {
            course.setTitle(updateDTO.getTitle());
        }
        if (updateDTO.getDescription() != null) {
            course.setDescription(updateDTO.getDescription());
        }
        if (updateDTO.getCoverUrl() != null) {
            course.setCoverUrl(updateDTO.getCoverUrl());
        }
        if (updateDTO.getDifficulty() != null) {
            course.setDifficulty(updateDTO.getDifficulty());
        }
        if (updateDTO.getDuration() != null) {
            course.setDuration(updateDTO.getDuration());
        }
        this.updateById(course);

        // 2. 重建 MySQL 关联关系
        Map<Integer, Tag> tagMap = courseTagService.getTagMapByIds(updateDTO.getTagIds());
        if (tagMap == null || tagMap.isEmpty()) {
            throw new IllegalArgumentException("Invalid tagIds");
        }
        baseMapper.deleteCourseCategoryRelations(course.getId());
        baseMapper.deleteCourseTagRelations(course.getId());
        baseMapper.deleteCourseKnowledgePointRelations(course.getId());

        baseMapper.insertCourseCategoryRelations(course.getId(), Collections.singletonList(updateDTO.getCategoryId()));
        baseMapper.insertCourseTagRelations(course.getId(), tagMap);
        baseMapper.insertCourseKnowledgePointRelations(course.getId(), updateDTO.getKnowledgePointIds());

        // 3. 同步 Neo4j 课程信息和知识点关系
        courseNodeRepository.upsertCourseTitle(course.getId(), course.getTitle());
        courseNodeRepository.clearKnowledgePoints(course.getId());
        courseNodeRepository.bindKnowledgePoints(course.getId(), updateDTO.getKnowledgePointIds());

        return true;
    }

    /**
     * 课程删除（逻辑删除）
     * 
     * @param courseIds 课程ID列表
     * @return 返回删除结果，成功返回提示信息，失败返回对应错误信息
     */
    @Override
    @Transactional(transactionManager = "transactionManager")
    public Integer removeCourses(List<Long> courseIds) {
        if (courseIds == null || courseIds.isEmpty()) {
            return 0;
        }
        // 1. 查询课程是否存在
        List<Course> courses = this.listByIds(courseIds);
        if (courses == null || courses.isEmpty()) {
            return -1; // 课程不存在
        }
        // 2. 物理删除课程
        Integer result = this.removeByIds(courseIds) ? courseIds.size() : 0;
        // 3. 批量删除 MySQL 关联关系
        baseMapper.deleteCourseCategoryRelationsByCourseIds(courseIds); // 删除课程分类关联
        baseMapper.deleteCourseTagRelationsByCourseIds(courseIds); // 删除课程标签关联
        baseMapper.deleteCourseKnowledgePointRelationsByCourseIds(courseIds); // 删除课程知识点关联
        // 4. 批量删除 Neo4j 课程节点和关系
        courseNodeRepository.deleteCourseGraphs(courseIds);
        if (result > 0) {
            try {
                // 删除主流程已经成功后，热榜清理只做告警，不再把“附属索引清理失败”升级成接口失败。
                // 否则会出现“课程其实已经删掉，但接口对外仍返回 500”的错位。
                learningAnalysisService.removeCourseHotBatch(courseIds);
            } catch (RuntimeException ex) {
                log.warn("删除课程后清理热榜失败，courseIds={}", courseIds, ex);
            }
            cleanupHotScoreSnapshots(courseIds);
        }
        return result;
    }

    @Override
    public String getCourseVideoPath(Long courseId) {
        return videoService.getVideoPath(courseId);
    }

    // 根据课程id获取课程详情（用户端）
    @Override
    public CourseDetailVO getCourseByIdForUser(Long courseId) {
        Course course = this.getById(courseId);
        if (course != null) {
            CourseDetailVO vo = courseMapperStruct.toDetailVO(course);
            return vo;
        }
        return null;
    }

    @Override
    public Map<Long, String> getCourseTitleMapByIds(List<Long> courseIds) {
        Map<Long, Course> courseMap = loadCourseMap(courseIds, baseMapper::selectCourseNamesByIds);
        Map<Long, String> titleMap = new HashMap<>();
        courseMap.forEach((courseId, course) -> titleMap.put(courseId, course.getTitle()));
        return titleMap;
    }

    @Override
    public Map<Long, Course> getRecommendCourseSummaryMapByIds(List<Long> courseIds) {
        // 这里返回 Map 而不是保留 List，是为了让推荐服务在按原始 courseIds 顺序组装结果时能 O(1) 取摘要。
        // IN 查询本身不保证结果顺序，真正的展示顺序仍由上层按照候选列表自行重建。
        return loadCourseMap(courseIds, baseMapper::selectRecommendCourseSummariesByIds);
    }

    @Override
    public Map<Long, Course> getOnlineRecommendCourseSummaryMapByIds(List<Long> courseIds) {
        // 热门兜底使用独立的“在线摘要”查询，是读取侧的最后一道保护：
        // 即使 Redis 热榜里还残留历史脏 id，也不会把下线课程重新返回给前台。
        return loadCourseMap(courseIds, baseMapper::selectOnlineRecommendCourseSummariesByIds);
    }

    private Map<Long, Course> loadCourseMap(List<Long> courseIds, Function<List<Long>, List<Course>> loader) {
        if (courseIds == null || courseIds.isEmpty()) {
            return new HashMap<>();
        }
        return courseListToMap(loader.apply(courseIds));
    }

    private Map<Long, Course> courseListToMap(List<Course> courses) {
        Map<Long, Course> result = new HashMap<>();
        if (courses == null || courses.isEmpty()) {
            return result;
        }
        for (Course course : courses) {
            if (course != null && course.getId() != null) {
                result.put(course.getId(), course);
            }
        }
        return result;
    }

    @Override
    public List<KnowledgeVO> getKnowledgePointsByCourseId(Long courseId) {
        List<Knowledge> knowledgePoints = baseMapper.selectKnowledgePointsByCourseId(courseId);
        if (knowledgePoints == null || knowledgePoints.isEmpty()) {
            return null;
        }
        return courseMapperStruct.toKnowledgeVOs(knowledgePoints);

    }

    /**
     * 更新课程状态。
     *
     * 新课冷启动依赖 publishTime 判断“是否新课”，因此在课程首次上线时写入 publishTime。
     * 这里采用“仅首次写入”策略：后续反复上下线不重置该时间，保证新课窗口语义稳定。
     */
    @Override
    public Boolean updateCourseStatus(Long courseId, Integer status) {
        Course course = this.getById(courseId);
        if (course == null) {
            return false; // 课程不存在
        }
        Integer oldStatus = course.getStatus();
        course.setStatus(status);
        // 仅在“首次转为上线”且 publishTime 为空时写入，避免历史上线时间被覆盖。
        if (CourseStatus.ONLINE.getCode() == status
                && (oldStatus == null || oldStatus != CourseStatus.ONLINE.getCode())
                && course.getPublishTime() == null) {
            course.setPublishTime(LocalDateTime.now());
        }
        boolean updated = this.updateById(course);
        if (updated && status != null && status != CourseStatus.ONLINE.getCode()) {
            try {
                // 课程状态更新属于主业务，热榜清理属于推荐侧附属动作。
                // 这里仅记录告警，避免在 Redis 短暂异常时把“状态已更新成功”的请求误报为失败。
                learningAnalysisService.removeCourseHot(courseId);
            } catch (RuntimeException ex) {
                log.warn("更新课程状态后清理热榜失败，courseId={}, status={}", courseId, status, ex);
            }
            cleanupHotScoreSnapshot(courseId);
        }
        return updated;
    }

    private void cleanupHotScoreSnapshot(Long courseId) {
        try {
            courseHotScoreMapper.deleteByCourseId(courseId);
        } catch (RuntimeException ex) {
            log.warn("清理课程热度快照失败，courseId={}", courseId, ex);
        }
    }

    private void cleanupHotScoreSnapshots(List<Long> courseIds) {
        try {
            courseHotScoreMapper.deleteByCourseIds(courseIds);
        } catch (RuntimeException ex) {
            log.warn("批量清理课程热度快照失败，courseIds={}", courseIds, ex);
        }
    }

    @Override
    public Boolean updateCourseDuration(Long courseId, Integer durationSeconds) {
        Course course = this.getById(courseId);
        if (course == null) {
            return false; // 课程不存在
        }
        course.setDuration(durationSeconds);
        return this.updateById(course);
    }

    @Override
    public CourseUpdateVO getCourseDetailForAdmin(Long courseId) {
        Course course = this.getById(courseId);
        if (course == null) {
            return null;
        }

        List<TagOptionDTO> tags = baseMapper.listEnabledTagOptionsByCourseId(courseId);
        List<KnowledgePointOptionDTO> knowledgePoints = baseMapper
                .listEnabledKnowledgePointOptionsByCourseId(courseId);

        CourseRegisterOptionsDTO options = new CourseRegisterOptionsDTO();
        options.setTags(tags);
        options.setKnowledgePoints(knowledgePoints);

        CourseUpdateVO vo = courseMapperStruct.toUpdateVO(course);
        vo.setCategoryId(baseMapper.selectCategoryIdByCourseId(courseId));
        vo.setOptions(options);
        return vo;

    }

}
