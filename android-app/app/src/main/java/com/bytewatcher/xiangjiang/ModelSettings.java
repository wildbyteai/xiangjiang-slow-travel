package com.bytewatcher.xiangjiang;

import android.content.Context;
import android.security.keystore.*;
import android.util.AtomicFile;
import org.json.JSONObject;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import java.security.KeyStore;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Private BYOK trial: encrypted config, no automatic requests, no backup/export. */
final class ModelSettings {
    private static final String ALIAS="xiangjiang-model-settings-v1";
    private final AtomicFile file;
    ModelSettings(Context c){file=new AtomicFile(new File(c.getNoBackupFilesDir(),"model-settings.enc"));}
    private SecretKey key()throws Exception{
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
        if(ks.containsAlias(ALIAS))return (SecretKey)ks.getKey(ALIAS,null);
        KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());return generator.generateKey();
    }
    JSONObject read()throws Exception{
        if(!file.getBaseFile().exists())return new JSONObject().put("endpoint","").put("model","").put("key","");
        byte[] bytes=file.readFully();if(bytes.length<29||bytes.length>16384)throw new IOException("模型配置无法读取；请重新配置，不影响日记");
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Arrays.copyOf(bytes,12)));return new JSONObject(new String(c.doFinal(Arrays.copyOfRange(bytes,12,bytes.length)),StandardCharsets.UTF_8));
    }
    void save(String endpoint,String model,String token)throws Exception{
        DiaryPolish.endpoint(endpoint);DiaryPolish.model(model);if(token.trim().isEmpty()||token.length()>4096||token.contains("\n")||token.contains("\r"))throw new IOException("请填写有效密钥");
        byte[] plain=new JSONObject().put("endpoint",endpoint.trim()).put("model",model.trim()).put("key",token.trim()).toString().getBytes(StandardCharsets.UTF_8);
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key());FileOutputStream out=null;
        try{out=file.startWrite();out.write(c.getIV());out.write(c.doFinal(plain));file.finishWrite(out);}catch(Exception e){if(out!=null)file.failWrite(out);throw e;}
    }
    void clear(){file.delete();}
}
