package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.entity.Sign;
import com.hmdp.service.SignService;
import com.hmdp.mapper.SignMapper;
import org.springframework.stereotype.Service;

/**
* @author lenovo
* @description 针对表【tb_sign】的数据库操作Service实现
* @createDate 2024-04-16 14:03:32
*/
@Service("signService")
public class SignServiceImpl extends ServiceImpl<SignMapper, Sign>
    implements SignService{

}




