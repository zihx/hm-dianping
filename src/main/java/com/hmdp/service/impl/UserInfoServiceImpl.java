package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.entity.UserInfo;
import com.hmdp.service.UserInfoService;
import com.hmdp.mapper.UserInfoMapper;
import org.springframework.stereotype.Service;

/**
* @author lenovo
* @description 针对表【tb_user_info】的数据库操作Service实现
* @createDate 2024-04-16 14:03:32
*/
@Service
public class UserInfoServiceImpl extends ServiceImpl<UserInfoMapper, UserInfo>
    implements UserInfoService{

}




