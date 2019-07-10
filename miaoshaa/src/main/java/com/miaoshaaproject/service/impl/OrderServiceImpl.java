package com.miaoshaaproject.service.impl;

import com.miaoshaaproject.dao.OrderDOMapper;
import com.miaoshaaproject.dao.SequenceDOMapper;
import com.miaoshaaproject.dataobject.OrderDO;
import com.miaoshaaproject.dataobject.SequenceDO;
import com.miaoshaaproject.error.BusinessException;
import com.miaoshaaproject.error.EmBusinessError;
import com.miaoshaaproject.service.ItemService;
import com.miaoshaaproject.service.OrderService;
import com.miaoshaaproject.service.UserService;
import com.miaoshaaproject.service.model.ItemModel;
import com.miaoshaaproject.service.model.OrderModel;
import com.miaoshaaproject.service.model.UserModel;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class OrderServiceImpl implements OrderService {
    @Autowired
    private ItemService itemService;
    @Autowired
    private UserService userService;
    @Autowired
    private OrderDOMapper orderDOMapper;
    @Autowired
    private SequenceDOMapper sequenceDOMapper;

    @Override
    @Transactional
    //需要用户id，商品id，购买数量
    public OrderModel createOrder(Integer userId, Integer itemId, Integer promoId,Integer amount) throws BusinessException {

        //1.校验下单状态，下单的商品是否存在，用户是否合法，购买数量是否正确。
        //判断商品是否存在
        ItemModel itemModel = itemService.getItemById(itemId);
        if(itemModel==null){
            throw  new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,"商品信息不存在");
        }
        //判断用户是否合法
        UserModel userModel = userService.getUserById(userId);
        if(userModel==null){
            throw  new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,"用户信息不存在");
        }

        //判断购买数量是否合法
        if(amount<=0||amount>=99){
            throw  new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,"商品信息不合法");
        }

        //校验活动信息，promoId不等于null
        if(promoId!=null){
            // 1校验对应活动是否存在这个适用商品
            //看传过来的秒杀模型id是否和商品模型中聚合的秒杀模型的id一致（该商品有秒杀活动，会将秒杀模型聚合进商品Model）
            if(promoId.intValue()!=itemModel.getPromoModel().getId()){
                throw  new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,"活动信息不正确");

                //即使id是秒杀模型的id，也不保险，还要校验是不是正在进行的秒杀
            }else if(itemModel.getPromoModel().getStatus()!=2){
                throw  new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,"秒杀不在进行中");
            }

        }


        //2.落单减库存，支付减库存。
        //采用落单减库存，itemService中提供一个减库存的方法
        boolean result = itemService.decreaseStock(itemId, amount);
        //返回false，库存不够扣的，下单数量大于库存量
        if(!result){
            throw  new BusinessException(EmBusinessError.STOCK_NOT_ENOUGH);
        }
        //否则，扣减库存正常开始
        //3.订单入库
        //创建OrderModel对象，封装数据。
        OrderModel orderModel = new OrderModel();
        orderModel.setUserId(userId);
        orderModel.setItemId(itemId);
        orderModel.setAmount(amount);
        //如果有秒杀，下单价格是秒杀价格
        if(promoId!=null){
            orderModel.setItemPrice(itemModel.getPromoModel().getPromoItemPrice());
        }else{
            //否则就是平销价格
            orderModel.setItemPrice(itemModel.getPrice());
        }


        //订单总价 商品单价X数量
        //先设置单价，设置完就已经明确了这个价格是秒杀价格还是平销价格了，再get出来
        orderModel.setOrderPrice(orderModel.getItemPrice().multiply(new BigDecimal(amount)));
        //给订单模型设置秒杀活动id
        orderModel.setPromoId(promoId);
        //生成交易流水号（订单号）
        orderModel.setId(generateOrderNo());

        //将OrderModel转为OrderDo
        OrderDO orderDO = convertFromOrderModel(orderModel);
        //保存订单
        orderDOMapper.insertSelective(orderDO);

        //增加该商品销量
        itemService.increaseSales(itemId,amount);


        //4.返回前端
        return orderModel;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String generateOrderNo(){

        StringBuffer sb = new StringBuffer();
        //订单号16位
        //前8位为时间信息，年月日，归档记录切分点
        LocalDateTime now = LocalDateTime.now();
        //格式化后的格式是2018-12-12带横线的，将-去掉
        String nowDate = now.format(DateTimeFormatter.ISO_DATE).replace("-","");
        sb.append(nowDate);

        //中间6位为自增序列，某一天的某一个时间点，订单号不重复
        //数据库中创建一张自增序列表sequence_info
        //从sequence_info表中获取当前序列值
        int sequence = 0;
        SequenceDO sequenceDO = sequenceDOMapper.getSequenceByName("order_info");

        //获取库中当前的序列值
        sequence = sequenceDO.getCurrentValue();
        //获取当前之后，生成新的，步长+1
        sequenceDO.setCurrentValue(sequenceDO.getCurrentValue()+sequenceDO.getStep());
        //之后马上更新表中的sequence
        sequenceDOMapper.updateByPrimaryKeySelective(sequenceDO);

        //凑足6位拼接序列
        String sequenceStr = String.valueOf(sequence);
        //序列值前面的几位补0
        for(int i=0;i<6-sequenceStr.length();i++){
            sb.append(0);
        }
        //将序列拼接上去
        //000001
        sb.append(sequenceStr);

        //最后2位为分库分表位，00-99，分库分表，订单水平拆分
        //订单信息落到拆分后的100个库的100张表中，分散数据库从查询和落单压力
        //订单号不变，这条订单记录一定会落到某一个库的某一张表上
//        Integer userId = 1000122;
//        userId % 100
        //暂时写死
        sb.append("00");

        return sb.toString();
    }
    //将Model转为dataObject
    private OrderDO convertFromOrderModel(OrderModel orderModel){
        if(orderModel==null){
            return null;
        }
        OrderDO orderDO = new OrderDO();
        BeanUtils.copyProperties(orderModel,orderDO);
        //单独处理商品价格和订单金额
        orderDO.setItemPrice(orderModel.getItemPrice());
        orderDO.setOrderPrice(orderModel.getOrderPrice());
        return orderDO;
    }
}