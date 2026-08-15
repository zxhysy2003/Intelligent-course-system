package com.sy.course_system.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.sy.course_system.common.UserContext;
import com.sy.course_system.common.UserInfo;
import com.sy.course_system.controller.client.AuthController;
import com.sy.course_system.controller.client.CourseController;
import com.sy.course_system.controller.client.LearningBehaviorRecordController;
import com.sy.course_system.controller.server.CourseAdminController;
import com.sy.course_system.controller.server.UserAdminController;
import com.sy.course_system.dto.UserUpdateDTO;
import com.sy.course_system.dto.course.CourseQueryDTO;
import com.sy.course_system.dto.course.CourseUpdateDTO;
import com.sy.course_system.entity.UserCourseRelation;
import com.sy.course_system.enums.LearnBehaviorType;
import com.sy.course_system.enums.UserType;
import com.sy.course_system.mapper.CategoryMapper;
import com.sy.course_system.service.CourseService;
import com.sy.course_system.service.LearningBehaviorService;
import com.sy.course_system.service.UserCourseService;
import com.sy.course_system.service.UserService;
import com.sy.course_system.service.VideoService;
import com.sy.course_system.service.VideoPlaybackService;
import com.sy.course_system.vo.CoursePlaybackVO;
import java.time.Instant;

@ExtendWith(MockitoExtension.class)
class ApiV1ControllerContractTest {
    @Mock
    private CourseService courseService;
    @Mock
    private CategoryMapper categoryMapper;
    @Mock
    private UserCourseService userCourseService;
    @Mock
    private LearningBehaviorService learningBehaviorService;
    @Mock
    private UserService userService;
    @Mock
    private VideoService videoService;
    @Mock
    private VideoPlaybackService videoPlaybackService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        CourseController courseController = new CourseController(
                courseService,
                categoryMapper,
                userCourseService,
                videoPlaybackService);

        LearningBehaviorRecordController behaviorController = new LearningBehaviorRecordController();
        ReflectionTestUtils.setField(behaviorController, "learningBehaviorService", learningBehaviorService);

        CourseAdminController courseAdminController = new CourseAdminController(courseService, videoService);

        UserAdminController userAdminController = new UserAdminController();
        ReflectionTestUtils.setField(userAdminController, "userService", userService);

        mockMvc = MockMvcBuilders.standaloneSetup(
                new AuthController(userService),
                courseController,
                behaviorController,
                courseAdminController,
                userAdminController)
                .build();
        UserContext.set(new UserInfo(10L, "tester", "ADMIN"));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void authUsesVersionedPublicResourcePaths() throws Exception {
        when(userService.login(any())).thenReturn("token");

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"tester\",\"password\":\"secret\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("token"));
    }

    @Test
    void learnerCourseWritesUsePostPatchAndJsonBodies() throws Exception {
        when(userCourseService.userAttendCourse(7L)).thenReturn(true);
        when(userCourseService.updateUserCourseRelation(any())).thenReturn(1);

        mockMvc.perform(post("/api/v1/courses/7/enrollment"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(patch("/api/v1/courses/7/enrollment/progress")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"progressSeconds\":36}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        ArgumentCaptor<UserCourseRelation> relation = ArgumentCaptor.forClass(UserCourseRelation.class);
        verify(userCourseService).updateUserCourseRelation(relation.capture());
        org.junit.jupiter.api.Assertions.assertEquals(7L, relation.getValue().getCourseId());
        org.junit.jupiter.api.Assertions.assertEquals(36, relation.getValue().getProgressSeconds());
    }

    @Test
    void playbackEndpointReturnsOnlySignedPlaybackMetadataAndDisablesCaching() throws Exception {
        when(videoPlaybackService.issue(10L, 7L)).thenReturn(new CoursePlaybackVO(
                "/videos/7/sample.mp4?token=signed",
                Instant.parse("2026-08-14T12:00:00Z")));

        mockMvc.perform(post("/api/v1/courses/7/playback"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.playbackUrl").value("/videos/7/sample.mp4?token=signed"))
                .andExpect(jsonPath("$.data.expiresAt").value("2026-08-14T12:00:00Z"));

        verify(videoPlaybackService).issue(10L, 7L);
    }

    @Test
    void learnerSearchForcesOnlineCoursesButAdminSearchKeepsRequestedScope() throws Exception {
        mockMvc.perform(post("/api/v1/courses/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"page\":1,\"status\":0}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/admin/courses/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"page\":1,\"status\":0}"))
                .andExpect(status().isOk());

        ArgumentCaptor<CourseQueryDTO> learnerQuery = ArgumentCaptor.forClass(CourseQueryDTO.class);
        ArgumentCaptor<CourseQueryDTO> adminQuery = ArgumentCaptor.forClass(CourseQueryDTO.class);
        verify(courseService).pageForUser(learnerQuery.capture());
        verify(courseService).pageForAdmin(adminQuery.capture());
        org.junit.jupiter.api.Assertions.assertEquals(1, learnerQuery.getValue().getStatus());
        org.junit.jupiter.api.Assertions.assertEquals(0, adminQuery.getValue().getStatus());
    }

    @Test
    void learningBehaviorUsesJsonBody() throws Exception {
        mockMvc.perform(post("/api/v1/learning-behaviors")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"courseId\":7,\"behaviorType\":\"STUDY\",\"duration\":12}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(learningBehaviorService).recordBehavior(7L, LearnBehaviorType.STUDY, 12);
    }

    @Test
    void adminCourseUpdateUsesPathIdAndBatchDeleteKeepsBody() throws Exception {
        when(courseService.update(any())).thenReturn(true);
        when(courseService.removeCourses(any())).thenReturn(2);
        when(courseService.updateCourseStatus(7L, 2)).thenReturn(true);

        mockMvc.perform(put("/api/v1/admin/courses/7")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"updated\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/admin/courses")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"courseIds\":[7,8]}"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/admin/courses/7/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":2}"))
                .andExpect(status().isOk());

        ArgumentCaptor<CourseUpdateDTO> update = ArgumentCaptor.forClass(CourseUpdateDTO.class);
        verify(courseService).update(update.capture());
        org.junit.jupiter.api.Assertions.assertEquals(7L, update.getValue().getId());
        verify(courseService).removeCourses(eq(java.util.List.of(7L, 8L)));
        verify(courseService).updateCourseStatus(7L, 2);
    }

    @Test
    void adminUserPatchAndUpdateUseJsonBodyAndPathId() throws Exception {
        when(userService.updateUserRole(3L, UserType.ADMIN)).thenReturn(true);
        when(userService.updateUserStatus(3L, 0)).thenReturn(true);
        when(userService.updateUser(any())).thenReturn(true);

        mockMvc.perform(patch("/api/v1/admin/users/3/role")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/admin/users/3/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":0}"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/admin/users/3")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"updated\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<UserUpdateDTO> update = ArgumentCaptor.forClass(UserUpdateDTO.class);
        verify(userService).updateUser(update.capture());
        org.junit.jupiter.api.Assertions.assertEquals(3L, update.getValue().getId());
    }

    @Test
    void removedAdminPathsAreNotRegistered() throws Exception {
        mockMvc.perform(post("/admin/user/list")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/admin/course/detail/7"))
                .andExpect(status().isNotFound());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/api/v1/courses/7/video"))
                .andExpect(status().isNotFound());
    }
}
