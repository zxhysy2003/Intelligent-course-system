package com.sy.course_system.service.impl;

import java.time.LocalDateTime;
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
import com.sy.course_system.dto.CourseAdminQueryDTO;
import com.sy.course_system.dto.CourseQueryDTO;
import com.sy.course_system.dto.CourseRegisterDTO;
import com.sy.course_system.dto.CourseTempDTO;
import com.sy.course_system.dto.CourseUpdateDTO;
import com.sy.course_system.entity.Course;
import com.sy.course_system.entity.UserCourseRelation;
import com.sy.course_system.mapper.CourseMapper;
import com.sy.course_system.mapper.mapperStruct.CourseMapperStruct;
import com.sy.course_system.repository.CourseNodeRepository;
import com.sy.course_system.service.CourseService;
import com.sy.course_system.service.LearningAnalysisService;
import com.sy.course_system.service.VideoService;
import com.sy.course_system.vo.CourseAdminVO;
import com.sy.course_system.vo.CourseVO;

@Service
public class CourseServiceImpl extends ServiceImpl<CourseMapper, Course> implements CourseService {
    @Autowired
    private CourseNodeRepository courseNodeRepository;

    @Autowired
    private LearningAnalysisService learningAnalysisService;

    @Autowired
    private VideoService videoService;

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

    // ===== 后台课程管理 =====

    @Override
    @Transactional(transactionManager = "transactionManager")
    public Integer register(CourseRegisterDTO registerDTO) {
        QueryWrapper<Course> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("title", registerDTO.getTitle());

        if (baseMapper.selectOne(queryWrapper) != null) {
            return -1; // 课程已存在
        }

        Course course = CourseMapperStruct.INSTANCE.toEntity(registerDTO);

        this.save(course);

        if (registerDTO.getCategoryIds() == null || registerDTO.getCategoryIds().isEmpty()) {
            return null;
        }
        // 关联课程分类
        baseMapper.insertCourseCategoryRelations(course.getId(), registerDTO.getCategoryIds());

        // 创建课程知识图谱根节点
        courseNodeRepository.createCourse(course.getId(), course.getTitle());

        return 1;
    }



    @Override
    public PageResult<CourseAdminVO> pageForAdmin(CourseAdminQueryDTO queryDTO) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'pageForAdmin'");
    }

    @Override
    public boolean update(CourseUpdateDTO updateDTO) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'update'");
    }

    @Override
    public boolean changeStatus(Long courseId, CourseStatus status) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'changeStatus'");
    }

    @Override
    public boolean delete(Long courseId) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'delete'");
    }

    @Override
    public boolean bindKnowledgePoints(Long courseId, List<Long> knowledgePointIds) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'bindKnowledgePoints'");
    }

    // ===== 用户是否已选修课程 =====
    @Override
    public Boolean userAttendCourse(Long courseId) {
        Long userId = UserContext.getUserId();
        Long count = baseMapper.countUserAttendCourse(userId, courseId);
        if (count != null && count > 0) {
            return false;
        }
        UserCourseRelation relation = new UserCourseRelation();
        relation.setUserId(userId);
        relation.setCourseId(courseId);
        relation.setProgress(0);
        relation.setLearnedSeconds(0);
        relation.setStatus(0); // 未开始
        relation.setLastLearnTime(LocalDateTime.now());
        relation.setCompleteTime(LocalDateTime.now());
        relation.setIsFavorite(0); // 非收藏

        baseMapper.insertUserCourseRelation(relation);
        return true;
    }

    @Override
    public String getCourseVideoPath(Long courseId) {
        return videoService.getVideoPath(courseId);
    }

}
