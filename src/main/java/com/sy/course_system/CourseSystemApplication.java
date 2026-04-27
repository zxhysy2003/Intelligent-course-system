package com.sy.course_system;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@MapperScan(basePackages = "com.sy.course_system.mapper", annotationClass = Mapper.class)
public class CourseSystemApplication {

	public static void main(String[] args) {
		SpringApplication.run(CourseSystemApplication.class, args);
	}

}
