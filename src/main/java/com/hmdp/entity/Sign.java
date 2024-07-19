package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.util.Date;
import lombok.Data;

/**
 * @TableName tb_sign
 */
@Data
@TableName(value ="tb_sign")
public class Sign implements Serializable {
    private Long id;

    private Long userId;

    private Object year;

    private Integer month;

    private Date date;

    private Integer isBackup;

    private static final long serialVersionUID = 1L;
}