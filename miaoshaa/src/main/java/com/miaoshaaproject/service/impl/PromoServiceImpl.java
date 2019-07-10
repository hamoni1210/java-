package com.miaoshaaproject.service.impl;


import com.miaoshaaproject.dao.PromoDOMapper;
import com.miaoshaaproject.dataobject.PromoDO;
import com.miaoshaaproject.service.PromoService;
import com.miaoshaaproject.service.model.PromoModel;
import org.joda.time.DateTime;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PromoServiceImpl implements PromoService {

    @Autowired
    private PromoDOMapper promoDOMapper;
    @Override
    public PromoModel getPromoByItemId(Integer itemId) {
        //获取对应商品的秒杀活动信息
        PromoDO promoDO = promoDOMapper.selectByItemId(itemId);

        //将Do转换成Model
        PromoModel promoModel = convertFromPromoDO(promoDO);

        //如果promoModel为null，没有该商品的秒杀活动
        if(promoModel==null){
            return null;
        }

        ///判断当前时间和秒杀开始时间的关系
        //开始时间在当前时间之后
        if(promoModel.getStartTime().isAfterNow()){
            //秒杀还未开始
            promoModel.setStatus(1);
        }else if(promoModel.getEndTime().isBeforeNow()){
            //秒杀已经结束
            promoModel.setStatus(3);
        }else{
            //秒杀正在进行
            promoModel.setStatus(2);
        }



        return promoModel;
    }
    private PromoModel convertFromPromoDO(PromoDO promoDO){

        if(promoDO ==null){
            return null;
        }

        PromoModel promoModel = new PromoModel();
        BeanUtils.copyProperties(promoDO,promoModel);
        //单独这是价格的
        promoModel.setPromoItemPrice(promoDO.getPromoItemPrice());
        //单独设置时间，mysql是sql.date,model是joda-date
        promoModel.setStartTime(new DateTime(promoDO.getStartTime()));
        promoModel.setEndTime(new DateTime(promoDO.getEndTime()));

        return promoModel;

    }
}
