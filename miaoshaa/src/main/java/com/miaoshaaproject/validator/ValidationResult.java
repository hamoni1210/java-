package com.miaoshaaproject.validator;



import java.util.HashMap;
import java.util.Map;

public class ValidationResult {
    private boolean hasErrors = false;
    private Map<String,String> errorMsgMap = new HashMap<>();

    public boolean isHasErrors() {
        return hasErrors;
    }

    public void setHasErrors(boolean hasErrors) {
        this.hasErrors = hasErrors;
    }

    public Map<String, String> getErrorMsgMap() {
        return errorMsgMap;
    }

    public void setErrorMsg(Map<String, String> errorMsg) {
        this.errorMsgMap = errorMsgMap;
    }
    //实现通用的通过格式化字符串信息获取错误结果的msg方法
    public String getErrMsg(){
        return org.apache.commons.lang3.StringUtils.join(errorMsgMap.values().toArray(),",");

    }
}