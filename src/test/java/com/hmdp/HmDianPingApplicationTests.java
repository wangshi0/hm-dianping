package com.hmdp;

import com.hmdp.service.impl.ShopServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class HmDianPingApplicationTests {

@Autowired
private ShopServiceImpl shopService;

    @Test
    void saveShop() {
        shopService.saveShop(1L,12L);
    }


}
