package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private ExecutorService executorService= Executors.newFixedThreadPool(10);
    @Override
    public Result queryShopBYId(Long id) {
//        Shop shop = queryWithThrough(String.valueOf(id));
//        Shop shop = queryWithWutex(String.valueOf(id));
        Shop shop = queryLogic(id);
        if (shop==null){
            return Result.fail("商品不存在");
        }
        return Result.ok(shop);

    }

    public Shop queryWithWutex(String id){
        String key = CACHE_SHOP_KEY + id;
        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(key);
        if (!entries.isEmpty()) {

            Shop shop = BeanUtil.fillBeanWithMap(entries, new Shop(), false);
//            System.out.println(shop);
//            System.out.println(JSONUtil.toJsonStr(shop));
            return shop;
        }

        if(entries.get(id)!=null){
            log.debug("111");
            log.debug(entries.toString());
            return null;
        }
        String lockKey= null;
        try {
            lockKey = "lock:shop:"+id;
            Boolean lock = getLock(lockKey);
            if (!lock){
                queryWithWutex(id);
                Thread.sleep(20);
            }
            Shop byId = getById(id);
//            Thread.sleep(200);
//        System.out.println(JSONUtil.toJsonStr(byId));
            if(byId!=null) {
                Map<String, Object> stringObjectMap = BeanUtil.beanToMap(byId,new HashMap<>(),
                        CopyOptions.create().
                                setIgnoreNullValue(true)
                                //.setFieldValueEditor((fieldName,fieldValue) -> fieldValue.toString()));
                                //解决方法：⭐在setFieldValueEditor中也需要判空
                                .setFieldValueEditor((fieldName,fieldValue) -> {
                                    if (fieldValue == null){
                                        fieldValue = "0";
                                    }else {
                                        fieldValue = fieldValue + "";
                                    }
                                    return fieldValue;
                                }));



                stringRedisTemplate.opsForHash().putAll(key, stringObjectMap);
//                stringRedisTemplate.expire(key,)
                stringRedisTemplate.expire(key, CACHE_SHOP_TTL, TimeUnit.MINUTES);
                return byId;
            }
            Map<String, Object> stringObjectMap =new HashMap<>();
            stringObjectMap.put("id","");
            stringRedisTemplate.opsForHash().putAll(key, stringObjectMap);
            stringRedisTemplate.expire(key, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }finally {
            delLock(lockKey);
        }
        return null;


    }

    public Shop queryLogic(Long id)  {
        String key = CACHE_SHOP_KEY + id;
        // 1、从redis中查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        // 2、判断是否存在
        if (StrUtil.isBlank(shopJson)) {
            // 存在,直接返回
            return null;
        }
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        JSONObject data = (JSONObject) redisData.getData();
        Shop shop = JSONUtil.toBean(data, Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        if(expireTime.isAfter(LocalDateTime.now())){
            return shop;
        }
        String lock=LOCK_SHOP_KEY+id;
        if (getLock(lock)) {
            executorService.submit(()-> {
                        try {
                            this.saveShop(id,10L);
                        } catch (Exception e) {
                            throw new RuntimeException();
                        } finally {
                            delLock(lock);
                        }
                    }
            );
        }
        return shop;



    }
    public Shop queryWithThrough(String id){
        String key = CACHE_SHOP_KEY + id;
        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(key);
        if (!entries.isEmpty()) {

            Shop shop = BeanUtil.fillBeanWithMap(entries, new Shop(), false);
            System.out.println(shop);
            System.out.println(JSONUtil.toJsonStr(shop));
            return shop;
        }
        if(entries.get(id)!=null){
            log.debug("111");
            log.debug(entries.toString());
            return null;
        }

        Shop byId = getById(id);
//        System.out.println(JSONUtil.toJsonStr(byId));
        if(byId!=null) {
            Map<String, Object> stringObjectMap = BeanUtil.beanToMap(byId,new HashMap<>(),
                    CopyOptions.create().
                            setIgnoreNullValue(true)
                            //.setFieldValueEditor((fieldName,fieldValue) -> fieldValue.toString()));
                            //解决方法：⭐在setFieldValueEditor中也需要判空
                            .setFieldValueEditor((fieldName,fieldValue) -> {
                                if (fieldValue == null){
                                    fieldValue = "0";
                                }else {
                                    fieldValue = fieldValue + "";
                                }
                                return fieldValue;
                            }));

            stringRedisTemplate.opsForHash().putAll(key, stringObjectMap);
            stringRedisTemplate.expire(key, CACHE_SHOP_TTL, TimeUnit.MINUTES);
            return byId;
        }
        Map<String, Object> stringObjectMap =new HashMap<>();
        stringObjectMap.put("id","");
        stringRedisTemplate.opsForHash().putAll(key, stringObjectMap);
        stringRedisTemplate.expire(key, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return null;
    }


    public Boolean getLock(String key){
        Boolean aBoolean = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(aBoolean);
    }

    public void delLock(String key){
        stringRedisTemplate.delete(key);
    }

    @Override
    public Result updateShop(Shop shop) {
        Long id = shop.getId();
        if(id==null){
            return Result.fail("id错误");
        }
        Shop byId = getById(id);


        updateById(shop);

        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }

    public void saveShop(Long id,Long expire){
        Shop byId = getById(id);
        LocalDateTime localDateTime = LocalDateTime.now().plusSeconds(expire);
        RedisData redisData = new RedisData();
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        redisData.setData(byId);
        redisData.setExpireTime(localDateTime);
//        stringRedisTemplate.opsForValue().set("2",redisData.toString());
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(redisData));
    }
}
