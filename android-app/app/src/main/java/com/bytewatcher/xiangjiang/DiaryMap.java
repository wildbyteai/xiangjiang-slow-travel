package com.bytewatcher.xiangjiang;
import org.json.*;
import java.util.*;

/** Sanitized local display data, not proof of arrival or a continuous GPS track. */
final class DiaryMap {
    static JSONArray points(JSONObject diary,String date)throws Exception{
        JSONArray out=new JSONArray(),list=diary.getJSONArray("records");
        for(int i=0;i<list.length();i++){
            JSONObject r=list.getJSONObject(i),l=r.optJSONObject("location");if(l==null||!date.isEmpty()&&!r.getString("occurredAt").startsWith(date))continue;
            JSONArray photos=r.getJSONArray("photos");JSONObject p=new JSONObject().put("id",r.getString("id")).put("latitude",l.getDouble("latitude")).put("longitude",l.getDouble("longitude")).put("time",r.getString("occurredAt")).put("place",r.optString("place")).put("photoCount",photos.length()).put("source",l.optString("source","record-location"));
            if(photos.length()>0)p.put("photo",photos.getJSONObject(0).getString("id"));out.put(p);
        }return out;
    }
    static boolean recordLink(String scheme,String host,String path){return "xiangjiang-record".equals(scheme)&&"open".equals(host)&&path!=null&&path.matches("/[a-f0-9-]{36}");}
}
