package com.bytewatcher.xiangjiang;
import org.junit.*;
import org.json.*;
import static org.junit.Assert.*;
import java.util.*;

public class DiaryMapTest {
    JSONObject record(String at,boolean located)throws Exception{JSONObject r=new JSONObject().put("id",UUID.randomUUID().toString()).put("text","PRIVATE_DIARY_TEXT").put("place","合成地点").put("occurredAt",at).put("stepId","sat-academy").put("photos",new JSONArray());if(located)r.put("location",new JSONObject().put("latitude",28.1).put("longitude",112.9));return r;}
    @Test public void filtersByActualDateNotStepPrefix()throws Exception{JSONObject a=record("2025-03-16T10:20",true),b=record("2025-03-15T15:00",true),c=record("2025-03-16T11:00",false);JSONObject diary=new JSONObject().put("records",new JSONArray().put(a).put(b).put(c));assertEquals(a.getString("id"),DiaryMap.points(diary,"2025-03-16").getJSONObject(0).getString("id"));assertEquals(1,DiaryMap.points(diary,"2025-03-16").length());assertEquals(2,DiaryMap.points(diary,"").length());assertFalse(DiaryMap.points(diary,"").toString().contains("PRIVATE_DIARY_TEXT"));}
    @Test public void imageReferenceIsLocalNotPhotoBytes()throws Exception{JSONObject r=record("2025-03-16T10:20",true);String id=UUID.randomUUID()+".jpg";r.getJSONArray("photos").put(new JSONObject().put("id",id));JSONObject p=DiaryMap.points(new JSONObject().put("records",new JSONArray().put(r)),"").getJSONObject(0);assertEquals(id,p.getString("photo"));assertEquals(1,p.getInt("photoCount"));assertFalse(p.has("text"));}
    @Test public void recordLinksAreStrictlyLocal()throws Exception{String id=UUID.randomUUID().toString();assertTrue(DiaryMap.recordLink("xiangjiang-record","open","/"+id));assertFalse(DiaryMap.recordLink("https","open","/"+id));assertFalse(DiaryMap.recordLink("xiangjiang-record","other","/"+id));assertFalse(DiaryMap.recordLink("xiangjiang-record","open","/../"+id));}
}
