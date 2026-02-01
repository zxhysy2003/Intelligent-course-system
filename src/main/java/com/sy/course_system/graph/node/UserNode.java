package com.sy.course_system.graph.node;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

@Node("User")
public class UserNode {
    @Id
    private Long id;

    public UserNode(Long id) {
        this.id = id;
    }

    public UserNode(){}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
}
