package com.sy.course_system.graph.node;


import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

@Node("Course")
public class CourseNode {
    @Id
    private Long id;

    private String title;

    public CourseNode(Long id, String title) {
        this.id = id;
        this.title = title;
    }
    public CourseNode(){}

    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }
    public String getTitle() {
        return title;
    }
    public void setTitle(String title) {
        this.title = title;
    }
    
}
