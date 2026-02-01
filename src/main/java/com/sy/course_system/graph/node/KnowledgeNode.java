package com.sy.course_system.graph.node;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

@Node("Knowledge")
public class KnowledgeNode {
    @Id
    private Long id;     // 与MySQL数据库中的知识点ID保持一致

    private String name; // 知识点名称

    private Integer difficulty; // 知识点难度等级

    public KnowledgeNode(Long id, String name, Integer difficulty) {
        this.id = id;
        this.name = name;
        this.difficulty = difficulty;
    }

    public KnowledgeNode() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(Integer difficulty) {
        this.difficulty = difficulty;
    }

    
}
