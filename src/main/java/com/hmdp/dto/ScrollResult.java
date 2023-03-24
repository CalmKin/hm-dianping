package com.hmdp.dto;

import lombok.Data;

import java.util.List;

@Data
public class ScrollResult {
    private List<?> list;
    //最小时间戳，上一次操作返回
    private Long minTime;
    private Integer offset;
}
