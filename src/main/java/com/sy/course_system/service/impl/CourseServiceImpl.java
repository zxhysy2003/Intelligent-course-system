package com.sy.course_system.service.impl;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.sy.course_system.enums.CourseOrderType;
import com.sy.course_system.enums.CourseStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.BeanUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sy.course_system.common.PageResult;
import com.sy.course_system.dto.CourseAdminQueryDTO;
import com.sy.course_system.dto.CourseQueryDTO;
import com.sy.course_system.dto.CourseRegisterDTO;
import com.sy.course_system.dto.CourseUpdateDTO;
import com.sy.course_system.entity.Course;
import com.sy.course_system.mapper.CourseMapper;
import com.sy.course_system.mapper.CourseMapperStruct;
import com.sy.course_system.repository.CourseNodeRepository;
import com.sy.course_system.service.CourseService;
import com.sy.course_system.service.LearningAnalysisService;
import com.sy.course_system.vo.CourseAdminVO;
import com.sy.course_system.vo.CourseVO;

@Service
public class CourseServiceImpl extends ServiceImpl<CourseMapper, Course> implements CourseService {
    @Autowired
    private CourseNodeRepository courseNodeRepository;

    @Autowired
    private LearningAnalysisService learningAnalysisService;

    @Override
    public Course getById(Long courseId) {
        return this.getById(courseId);
    }

    @Override
    public List<Course> listByIds(List<Long> courseIds) {
        if (courseIds == null || courseIds.isEmpty()) {
            return Collections.emptyList();
        }
        return baseMapper.selectList(new LambdaQueryWrapper<Course>()
                .in(Course::getId, courseIds)
                .eq(Course::getStatus, 1));
    }

    @Override
    public Map<Long, Course> mapByIds(List<Long> courseIds) {
        List<Course> coursesList = listByIds(courseIds);

        // 转Map，方便推荐结果组装
        return coursesList.stream()
                .collect(Collectors.toMap(
                        Course::getId,
                        course -> course));
    }

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
    public List<Long> getKnowledgePointIdsByCourseId(Long courseId) {
        return baseMapper.selectKnowledgePointIdsByCourseId(courseId);
    }

    @Override
    public PageResult<CourseVO> pageForUser(CourseQueryDTO dto) {
        // 1.分页参数
        int page = dto.getPage() != null && dto.getPage() > 0 ? dto.getPage() : 1;
        int pageSize = dto.getPageSize() != null && dto.getPageSize() > 0 ? dto.getPageSize() : 10;
        
        // 2.构建分页对象
        Page<Course> pageParam = new Page<>(page, pageSize);

        // 3.构建查询条件
        LambdaQueryWrapper<Course> qw = new LambdaQueryWrapper<>();

        // 只显示上线的课程
        qw.eq(Course::getStatus, CourseStatus.ONLINE.getCode());

        // 关键词搜索
        if (StringUtils.hasText(dto.getKeyword())) {
            qw.like(Course::getTitle, dto.getKeyword());
        }

        // 分类搜索：根据关系表筛选拥有指定分类的课程
        if (dto.getCategoryId() != null) {
            qw.inSql(Course::getId,
                "select course_id from course_category_relation where category_id = " + dto.getCategoryId());
        }

        // 推荐结果过滤
        if (dto.getCourseIds() != null && !dto.getCourseIds().isEmpty()) {
            qw.in(Course::getId, dto.getCourseIds());
        }

        // 4.普通排序（数据库支持）
        if (dto.getOrderBy() == CourseOrderType.NEW) {
            qw.orderByDesc(Course::getCreateTime);
        } else if (dto.getOrderBy() != CourseOrderType.HOT && dto.getOrderBy() != CourseOrderType.SCORE) {
            qw.orderByDesc(Course::getId); // 默认按最新排序
        }

        // 5.执行分页查询
        Page<Course> coursePage = this.page(pageParam, qw);
        List<Course> courses = coursePage.getRecords();
        
        // 6.热度排序 (Redis 热度)
        if (dto.getOrderBy() == CourseOrderType.HOT && courses != null && !courses.isEmpty()) {
            courses = learningAnalysisService.sortCoursesByHotness(courses);
        }

        // 7.封装结果并返回
        List<CourseVO> voList = convertToVO(courses, dto);

        return PageResult.of(coursePage.getTotal(), page, pageSize, voList);
    }

    // TODO: 完善 CourseVO 转换逻辑
    private List<CourseVO> convertToVO(List<Course> courses, CourseQueryDTO dto) {
        return null;
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

}
