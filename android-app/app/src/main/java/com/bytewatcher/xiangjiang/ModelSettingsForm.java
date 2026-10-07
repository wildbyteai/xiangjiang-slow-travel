package com.bytewatcher.xiangjiang;

import org.json.JSONObject;
import java.io.IOException;
import java.net.URI;

/** Local form rules only; no networking, file writes or credential logging. */
final class ModelSettingsForm {
    static final class Invalid extends IOException {
        final String field;
        Invalid(String field,String message){super(message);this.field=field;}
    }
    static JSONObject prepare(String address,String model,String enteredKey,JSONObject existing)throws Exception {
        URI next;
        try{next=DiaryPolish.endpoint(address);}catch(IOException e){throw new Invalid("endpoint","请填完整 HTTPS /chat/completions 地址，不含密钥或查询参数");}
        try{DiaryPolish.model(model);}catch(IOException e){throw new Invalid("model","请填写支持图片输入的模型名，最多120字");}
        String key=enteredKey.trim();
        if(key.isEmpty()){
            String previous=existing.optString("endpoint");
            if(!previous.isEmpty()&&!next.getAuthority().equals(DiaryPolish.endpoint(previous).getAuthority()))throw new Invalid("key","服务域名或端口变化，请重新填写该服务的密钥");
            key=existing.optString("key");
        }
        if(key.trim().isEmpty()||key.length()>4096||key.contains("\n")||key.contains("\r"))throw new Invalid("key","首次配置需要密钥；密钥不能包含换行，最多4096字");
        return new JSONObject().put("endpoint",next.toString()).put("model",model.trim()).put("key",key.trim());
    }
    static boolean changed(String address,String model,String enteredKey,JSONObject existing){
        return !address.trim().equals(existing.optString("endpoint"))||!model.trim().equals(existing.optString("model"))||!enteredKey.isEmpty();
    }
}
