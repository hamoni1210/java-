package com.miaoshaaproject.controller;

import com.miaoshaaproject.controller.viewobject.ItemVO;
import com.miaoshaaproject.error.BusinessException;
import com.miaoshaaproject.response.CommonReturnType;
import com.miaoshaaproject.service.ItemService;
import com.miaoshaaproject.service.model.ItemModel;
import org.joda.time.format.DateTimeFormat;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Controller("/item")
@RequestMapping("/item")
@CrossOrigin(origins = "*",allowCredentials = "true")
public class ItemController extends BaseController{
    @Autowired
    private ItemService itemService;
    //创建商品的controller
    @RequestMapping(value = "/create",method = {RequestMethod.POST},consumes = {CONTENT_TYPE_FORMED})
    @ResponseBody
    public CommonReturnType createItem(@RequestParam(name="title") String title,
                                       @RequestParam(name="description") String description,
                                       @RequestParam(name="price") BigDecimal price,
                                       @RequestParam(name="stock") Integer stock,
                                       @RequestParam(name="imgUrl") String imgUrl) throws BusinessException {
        //封装service请求用来创建商品
        ItemModel itemModel = new ItemModel();
        itemModel.setTitle(title);
        itemModel.setDescription(description);
        itemModel.setPrice(price);
        itemModel.setStock(stock);
        itemModel.setImgUrl(imgUrl);

        ItemModel itemModelForReturn  = itemService.createItem(itemModel);
        ItemVO itemVO = covertVOFromModel(itemModelForReturn);

        return CommonReturnType.create(itemVO);

    }

//    //商品详情页浏览
    @RequestMapping(value = "/get",method = {RequestMethod.GET})
    @ResponseBody
    public CommonReturnType getItem(@RequestParam(name = "id")Integer id){
        ItemModel itemModel = itemService.getItemById(id);

        ItemVO itemVO = covertVOFromModel(itemModel);

        return CommonReturnType.create(itemVO);
    }

    //商品列表页浏览
    @RequestMapping(value = "/list",method = {RequestMethod.GET})
    @ResponseBody
    public CommonReturnType listItem(){
        List<ItemModel> itemModelList =  itemService.listItem();
        //将model转为vo
        //使用stream api 将list内的itemModel转为ItemVO
        List<ItemVO> itemVOList = itemModelList.stream().map(itemModel -> {

            ItemVO itemVO = this.covertVOFromModel(itemModel);
            return itemVO;

        }).collect(Collectors.toList());

        return CommonReturnType.create(itemVOList);
    }


    private ItemVO covertVOFromModel(ItemModel itemModel){
        if(itemModel == null){
            return null;
        }
        ItemVO itemVO = new ItemVO();
        BeanUtils.copyProperties(itemModel,itemVO);
        if(itemModel.getPromoModel()!=null){
            //有正在进行或即将开始的秒杀活动
            //设置秒杀活动状态
            itemVO.setPromoStatus(itemModel.getPromoModel().getStatus());
            //设置秒杀活动id
            itemVO.setPromoId(itemModel.getPromoModel().getId());
            //设置秒杀活动开始时间
            itemVO.setStartTime(itemModel.getPromoModel().getStartTime().toString(DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss")));
            //秒杀价格
            itemVO.setPromoPrice(itemModel.getPromoModel().getPromoItemPrice());
        }else{
            //该商品没有秒杀活动
            itemVO.setPromoStatus(0);
        }
        return itemVO;
    }

}
