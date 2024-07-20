package com.hmdp.service;

import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import com.baomidou.mybatisplus.extension.service.IService;

/**
* @author lenovo
* @description 针对表【tb_user】的数据库操作Service
* @createDate 2024-04-16 14:03:32
*/
public interface UserService extends IService<User> {

    Result sendCode(String phone);

    Result login(LoginFormDTO loginForm);

    Result queryById(Long id);

    Result sign();

    Result signCount();
}
