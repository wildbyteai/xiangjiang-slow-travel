package com.bytewatcher.xiangjiang;

import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class TripDatesTest {
    JSONObject trip()throws Exception{return new JSONObject().put("days",new JSONArray()
        .put(new JSONObject().put("id","sat").put("date","2025-03-15").put("theme","岳麓书院"))
        .put(new JSONObject().put("id","sun").put("date","2025-03-16").put("theme","橘子洲")));}
    @Test public void datesComeFromTripData()throws Exception{assertEquals("2025-03-15",TripDates.date(trip(),0));assertEquals("2025-03-16",TripDates.date(trip(),1));assertEquals("",TripDates.date(trip(),5));}
    @Test public void malformedDateIsIgnored()throws Exception{JSONObject t=trip();t.getJSONArray("days").getJSONObject(0).put("date","下周六");assertEquals("",TripDates.date(t,0));assertEquals(-1,TripDates.dayOf(t,"下周六"));}
    @Test public void headingNamesPlannedDayAndWeekday()throws Exception{assertEquals("第一天 · 岳麓书院 · 3月15日 周六",TripDates.heading(trip(),"2025-03-15"));assertEquals("第二天 · 橘子洲 · 3月16日 周日",TripDates.heading(trip(),"2025-03-16"));}
    @Test public void unplannedDayKeepsCalendarOnly()throws Exception{assertEquals("3月20日 周四",TripDates.heading(trip(),"2025-03-20"));assertEquals("坏日期",TripDates.heading(trip(),"坏日期"));}
}
