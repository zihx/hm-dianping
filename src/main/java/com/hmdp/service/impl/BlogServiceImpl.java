package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.entity.Blog;
import com.hmdp.service.BlogService;
import com.hmdp.mapper.BlogMapper;
import org.springframework.stereotype.Service;

/**
* @author lenovo
* @description 针对表【tb_blog】的数据库操作Service实现
* @createDate 2024-04-16 14:03:32
*/
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog>
    implements BlogService{

}




