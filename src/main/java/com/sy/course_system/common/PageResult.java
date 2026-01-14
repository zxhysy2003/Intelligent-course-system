package com.sy.course_system.common;

import java.util.List;

public class PageResult<T> {
    private Long total;        // 总条数
    private Integer page;      // 当前页
    private Integer size;      // 每页大小
    private List<T> records;   // 当前页数据

    public static <T> PageResult<T> of(
            Long total,
            Integer page,
            Integer size,
            List<T> records) {

        PageResult<T> r = new PageResult<>();
        r.setTotal(total);
        r.setPage(page);
        r.setSize(size);
        r.setRecords(records);
        return r;
    }

    public Long getTotal() {
        return total;
    }

    public void setTotal(Long total) {
        this.total = total;
    }

    public Integer getPage() {
        return page;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public Integer getSize() {
        return size;
    }

    public void setSize(Integer size) {
        this.size = size;
    }

    public List<T> getRecords() {
        return records;
    }

    public void setRecords(List<T> records) {
        this.records = records;
    }

    
}
