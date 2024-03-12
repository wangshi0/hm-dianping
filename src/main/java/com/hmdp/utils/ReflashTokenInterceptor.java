package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;

public class ReflashTokenInterceptor implements HandlerInterceptor {


    private StringRedisTemplate redisTemplate;

    public ReflashTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.redisTemplate=stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {


        String token = request.getHeader("authorization");
        if (StrUtil.isBlank(token)){
            return true;
        }
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(LOGIN_USER_KEY + token);

        if (entries.isEmpty()) {
            return true;
        }
        UserDTO userDTO = BeanUtil.fillBeanWithMap(entries, new UserDTO(), false);


        UserHolder.saveUser(userDTO);
        // 有用户，则放行
        redisTemplate.expire(LOGIN_USER_KEY + token,LOGIN_USER_TTL, TimeUnit.MINUTES);
        return true;
    }
}
