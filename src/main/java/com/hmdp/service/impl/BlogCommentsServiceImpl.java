package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.entity.BlogComments;
import com.hmdp.service.BlogCommentsService;
import com.hmdp.mapper.BlogCommentsMapper;
import org.springframework.stereotype.Service;

/**
* @author lenovo
* @description 针对表【tb_blog_comments】的数据库操作Service实现
* @createDate 2024-04-16 14:03:32
*/
@Service("blogCommentsService")
public class BlogCommentsServiceImpl extends ServiceImpl<BlogCommentsMapper, BlogComments>
    implements BlogCommentsService{

}




