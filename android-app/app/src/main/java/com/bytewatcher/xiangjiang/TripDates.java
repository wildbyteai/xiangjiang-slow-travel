package com.bytewatcher.xiangjiang;

import org.json.*;
import java.time.*;

/** Trip calendar comes only from trip.json, so changing the itinerary never needs a Java edit. */
final class TripDates {
    private static final String[] WEEK={"周一","周二","周三","周四","周五","周六","周日"};
    private static final String[] ORDINAL={"第一天","第二天","第三天","第四天","第五天","第六天","第七天"};
    private TripDates(){}
    /** ISO date (yyyy-MM-dd) of the given trip day, or "" when the day is missing or malformed. */
    static String date(JSONObject trip,int day){
        JSONArray days=trip==null?null:trip.optJSONArray("days");JSONObject d=days==null?null:days.optJSONObject(day);
        String value=d==null?"":d.optString("date");
        try{LocalDate.parse(value);return value;}catch(Exception e){return "";}
    }
    /** Index of the trip day that falls on this ISO date, or -1. */
    static int dayOf(JSONObject trip,String isoDate){
        JSONArray days=trip==null?null:trip.optJSONArray("days");if(days==null||isoDate==null)return -1;
        for(int i=0;i<days.length();i++)if(isoDate.equals(date(trip,i)))return i;return -1;
    }
    /** Diary heading: "第一天 · 岳麓书院 · 3月15日 周六", or "3月20日 周四" for days outside the plan. */
    static String heading(JSONObject trip,String isoDate){
        LocalDate d;try{d=LocalDate.parse(isoDate);}catch(Exception e){return isoDate==null?"":isoDate;}
        String calendar=d.getMonthValue()+"月"+d.getDayOfMonth()+"日 "+WEEK[d.getDayOfWeek().getValue()-1];
        int i=dayOf(trip,isoDate);if(i<0)return calendar;
        String theme=trip.optJSONArray("days").optJSONObject(i).optString("theme");
        return (i<ORDINAL.length?ORDINAL[i]:"第"+(i+1)+"天")+(theme.isEmpty()?"":" · "+theme)+" · "+calendar;
    }
}
