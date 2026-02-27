package com.sy.course_system.service.impl;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.sy.course_system.enums.CourseOrderType;
import com.sy.course_system.enums.CourseStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sy.course_system.common.PageResult;
import com.sy.course_system.common.UserContext;
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
import com.sy.course_system.mapper.CourseMapper;
import com.sy.course_system.mapper.KnowledgePointMapper;
import com.sy.course_system.mapper.TagMapper;
import com.sy.course_system.mapper.mapperStruct.CourseMapperStruct;
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
    @Autowired
    private CourseNodeRepository courseNodeRepository;

    @Autowired
    private LearningAnalysisService learningAnalysisService;

    @Autowired
    private VideoService videoService;

    @Autowired
    private CourseTagService courseTagService;

    @Autowired
    private TagMapper tagMapper;
    
    @Autowired
    private KnowledgePointMapper knowledgePointMapper;

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
        Page<CourseTempDTO> pageParam = new Page<>(page, pageSize);

        // 3.执行分页查询
        Page<CourseTempDTO> coursePage = baseMapper.selectCoursePage(pageParam, UserContext.getUserId(), dto);
        Long total = baseMapper.selectCoursePageCount(dto);
        coursePage.setTotal(total);

        // 4.转换为VO
        List<CourseTempDTO> courseTempList = coursePage.getRecords();
        List<CourseVO> courses = convertToVO(courseTempList, dto);

        // 5.返回分页结果
        return PageResult.of(coursePage.getTotal(), page, pageSize, courses);


    }

    private List<CourseVO> convertToVO(List<CourseTempDTO> courseTempList, CourseQueryDTO dto) {
        List<CourseVO> courses = courseTempList.stream()
                .map(CourseMapperStruct.INSTANCE::tempToVO)
                .collect(Collectors.toList());
    
        Integer sortBy = dto.getSortBy();
        
        // 热度排序特殊处理
        if (CourseOrderType.HOT.getCode().equals(sortBy)) {
            return learningAnalysisService.sortCoursesByHotness(courses);
        }
        
        // 定义排序规则映射
        Map<Integer, Comparator<CourseVO>> sortStrategies = Map.of(
            CourseOrderType.DEFAULT.getCode(), Comparator.comparing(CourseVO::getLearners).reversed(),
            CourseOrderType.DIFFICULTY.getCode(), Comparator.comparing(CourseVO::getDifficulty),
            CourseOrderType.PROGRESS.getCode(), Comparator.comparing(CourseVO::getProgress).reversed(),
            CourseOrderType.NEW.getCode(), Comparator.comparing(CourseVO::getLastTime).reversed(),
            CourseOrderType.SCORE.getCode(), Comparator.comparing(CourseVO::getScore).reversed()
        );
        
        Comparator<CourseVO> comparator = sortStrategies.get(sortBy);
        if (comparator != null) {
            courses.sort(comparator);
        }
        
        return courses;
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
        return CourseMapperStruct.INSTANCE.toDetailVOs(courses);
    }

    // ===== 后台课程管理 =====

    /**
     * 课程注册
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

        if (registerDTO.getCategoryId() == null || registerDTO.getTagIds() == null || registerDTO.getTagIds().isEmpty() || registerDTO.getKnowledgePointIds() == null || registerDTO.getKnowledgePointIds().isEmpty()) {
            throw new IllegalArgumentException("categoryId or tagIds or kpIds is empty");
        }

        Map<Integer, Tag> tagMap = courseTagService.getTagMapByIds(registerDTO.getTagIds());
        if (tagMap == null || tagMap.isEmpty()) {
            throw new IllegalArgumentException("Invalid tagIds");
        }

        Course course = CourseMapperStruct.INSTANCE.toEntity(registerDTO);
        course.setStatus(CourseStatus.DRAFT.getCode()); // 默认草稿状态,待上传完视频后再上线

        this.save(course);

        // 关联课程分类
        baseMapper.insertCourseCategoryRelations(course.getId(), Collections.singletonList(registerDTO.getCategoryId()));
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
            CourseDetailVO vo = CourseMapperStruct.INSTANCE.toDetailVO(course);
            return vo;
        }
        return null;
    }

    @Override
    public Map<Long, String> getCourseTitleMapByIds(List<Long> courseIds) {
        Map<Long, String> result = new java.util.HashMap<>();
        List<Course> courses = baseMapper.selectCourseNamesByIds(courseIds);
        if (courses == null || courses.isEmpty()) {
            return result;
        }
        for (Course course : courses) {
            if (course != null && course.getId() != null) {
                result.put(course.getId(), course.getTitle());
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
        return knowledgePoints.stream()
                .map(kp -> {
                    KnowledgeVO vo = new KnowledgeVO();
                    vo.setId(kp.getId());
                    vo.setName(kp.getName());
                    vo.setDifficulty(kp.getDifficulty());
                    return vo;
                })
                .collect(Collectors.toList());
        
    }

    @Override
    public Boolean updateCourseStatus(Long courseId, Integer status) {
        Course course = this.getById(courseId);
        if (course == null) {
            return false; // 课程不存在
        }
        course.setStatus(status);
        return this.updateById(course);
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
        
        List<Long> tagIds = baseMapper.selectTagIdsByCourseId(courseId);
        List<TagOptionDTO> tags = tagMapper.listEnabledTagOptionsByIds(tagIds);
        List<Long> kpIds = baseMapper.selectKnowledgePointIdsByCourseId(courseId);
        List<KnowledgePointOptionDTO> knowledgePoints = knowledgePointMapper.listEnabledKnowledgePointOptionsByIds(kpIds);

        CourseRegisterOptionsDTO options = new CourseRegisterOptionsDTO();
        options.setTags(tags);
        options.setKnowledgePoints(knowledgePoints);

        CourseUpdateVO vo = new CourseUpdateVO();
        vo.setId(course.getId());
        vo.setTitle(course.getTitle());
        vo.setDescription(course.getDescription());
        vo.setCoverUrl(course.getCoverUrl());
        vo.setDifficulty(course.getDifficulty());
        vo.setDuration(course.getDuration());
        vo.setCategoryId(baseMapper.selectCategoryIdByCourseId(courseId));
        vo.setOptions(options);
        return vo;
        
    }


}
