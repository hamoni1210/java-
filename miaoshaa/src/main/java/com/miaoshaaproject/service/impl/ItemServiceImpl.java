package com.miaoshaaproject.service.impl;

import com.miaoshaaproject.dao.ItemDOMapper;
import com.miaoshaaproject.dao.ItemStockDOMapper;
import com.miaoshaaproject.dataobject.ItemDO;
import com.miaoshaaproject.dataobject.ItemStockDO;
import com.miaoshaaproject.error.BusinessException;
import com.miaoshaaproject.error.EmBusinessError;
import com.miaoshaaproject.service.ItemService;
import com.miaoshaaproject.service.PromoService;
import com.miaoshaaproject.service.model.ItemModel;
import com.miaoshaaproject.service.model.PromoModel;
import com.miaoshaaproject.validator.ValidationResult;
import com.miaoshaaproject.validator.ValidatorImpl;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ItemServiceImpl implements ItemService {

    //引入自定义的校验器
    @Autowired
    private ValidatorImpl validator;


    //注入ItemMapper组件
    @Autowired
    private ItemDOMapper itemDOMapper;

    //注入ItemStockMapper组件
    @Autowired
    private ItemStockDOMapper itemStockDOMapper;

    //注入秒杀活动组件
    @Autowired
    private PromoService promoService;

    //创建商品，需要事务，在方法上添加
    @Override
    @Transactional
    public ItemModel createItem(ItemModel itemModel) throws BusinessException {

        //首先进行入参校验
        ValidationResult validationResult = validator.validate(itemModel);

        if(validationResult.isHasErrors()){

            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,validationResult.getErrMsg());
        }

        //将ItemModel转为ItemDo（dataObject）
        ItemDO itemDO = convertItemDOFromItemModel(itemModel);

        //将ItemDo写入数据库
        //写入后返回了itemDo的id
        itemDOMapper.insertSelective(itemDO);
        //将id给itemModel
        itemModel.setId(itemDO.getId());
        //itemModel转itemStockDo
        ItemStockDO itemStockDO = convertItemStockDoFromItemModel(itemModel);

        //将ItemStockdo写入数据库
        itemStockDOMapper.insertSelective(itemStockDO);


        //返回创建完成的对象,通过调getItemById完成
        return this.getItemById(itemModel.getId());
    }

    //将ItemModel转为ItemDo的转换方法
    private ItemDO convertItemDOFromItemModel(ItemModel itemModel){

        if(itemModel==null){
            return null;
        }

        ItemDO itemDO = new ItemDO();

        //UserModel中的price是BigDecimal类型而不用Double，Double在java内部传到前端，会有精度问题，不精确
        //有可能1.9，显示时是1.999999，为此在Service层，将price定为比较精确的BigDecimal类型
        //但是在拷贝到Dao层时，存入的是Double类型，拷贝方法对应类型不匹配的属性，不会进行拷贝。
        //在拷贝完，将BigDecimal转为Double，再set进去
        BeanUtils.copyProperties(itemModel,itemDO);
        //转为double

        return itemDO;
    }

    //从itemModel中取stock和id转为ItemStockDo方法
    private ItemStockDO convertItemStockDoFromItemModel(ItemModel itemModel){
        if(itemModel == null){
            return null;
        }
        ItemStockDO itemStockDO = new ItemStockDO();
        itemStockDO.setItemId(itemModel.getId());
        itemStockDO.setStock(itemModel.getStock());
        return itemStockDO;
    }

    @Override
    public List<ItemModel> listItem() {

        List<ItemDO> itemDOList = itemDOMapper.listItem();
        //遍历List，每一个itemDo转为ItemModel
        List<ItemModel> itemModelList = itemDOList.stream().map(itemDO -> {
            //加入itemstockDo，获取库存
            ItemStockDO itemStockDO = itemStockDOMapper.selectByItemId(itemDO.getId());
            ItemModel itemModel = this.convertModelFromDataObject(itemDO,itemStockDO);

            return itemModel;
            //转为List集合
        }).collect(Collectors.toList());

        return itemModelList;
    }

    /**
     * 根据商品id查询商品
     * @param id
     * @return
     * 先查出itemDo
     * 再查出对应的stock，封装成itemModel
     */
    @Override
    public ItemModel getItemById(Integer id) {

        ItemDO itemDO = itemDOMapper.selectByPrimaryKey(id);
        if(itemDO==null){
            return null;
        }
        //根据item_id查出stock
        ItemStockDO itemStockDO = itemStockDOMapper.selectByItemId(itemDO.getId());
        ItemModel itemModel = convertModelFromDataObject(itemDO, itemStockDO);

        //获取活动商品信息
        PromoModel promoModel = promoService.getPromoByItemId(itemModel.getId());

        //如果存在该商品秒杀对象并且秒杀状态不等于3,说明秒杀有效
        if(promoModel!=null && promoModel.getStatus().intValue()!=3){
            //将秒杀对象聚合进ItemModel，将该商品和秒杀对象关联起来
            itemModel.setPromoModel(promoModel);
        }


        return itemModel;

    }


    //扣减库存
    @Override
    @Transactional
    public boolean decreaseStock(Integer itemId, Integer amount) throws BusinessException {
        /*
            item商品表大部分用户查询，查询对应的商品信息
            库存表，在某些高压力的情况下做降级
            比如在微服务下，库存服务可以拆为item的展示服务（item表）和item的库存服务（item_stock表）
            这个item的库存服务独立出来，专门进行库存减操作。
            目前只操作item_stock表，为保证冻结操作的原子性，对item_stock表加锁，针对某一条记录进行加行锁，减掉对应的库存
            看减完后是否还大于表中库存。
            修改itemStockDoMapper映射文件，修改sql语句
         */
        //返回影响的条目数
        //sql成功执行返回的影响条目数不一定为1，如果购买数量大于库存，超卖，sql语句也会执行，但返回的就是0
        int affectRow = itemStockDOMapper.decreaseStock(itemId, amount);
        if(affectRow>0){
            //更新库存成功
            return true;
        }else{
            return false;
        }

    }

    /**
     * 商品销量增加
     * @param id
     * @param amount
     * @throws BusinessException
     */
    @Override
    @Transactional
    public void increaseSales(Integer id, Integer amount) throws BusinessException {

        itemDOMapper.increaseSales(id,amount);

    }

    //将dataobject转换成Model领域模型
    private ItemModel convertModelFromDataObject(ItemDO itemDO, ItemStockDO itemStockDO){

        ItemModel itemModel = new ItemModel();
        BeanUtils.copyProperties(itemDO,itemModel);
        itemModel.setStock(itemStockDO.getStock());

        return itemModel;

    }
}