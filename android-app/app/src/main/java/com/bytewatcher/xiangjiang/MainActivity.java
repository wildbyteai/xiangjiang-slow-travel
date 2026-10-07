package com.bytewatcher.xiangjiang;

import android.Manifest;
import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.location.*;
import androidx.exifinterface.media.ExifInterface;
import android.net.Uri;
import android.provider.MediaStore;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import androidx.core.content.FileProvider;
import org.json.*;
import java.io.*;
import java.nio.file.Files;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    private static final int INK=0xff233934, GREEN=0xff126b63, PAPER=0xfffafcfb, MUTED=0xff6a7b75;
    private static final int CAMERA=20, PICK=21, HTML=22, BACKUP=23, RESTORE=24, PERMISSION=25;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final ExecutorService aiWorker=Executors.newSingleThreadExecutor();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private DiaryStore store; private JSONObject trip,draft,location;
    private JSONArray draftPhotos=new JSONArray();
    private LinearLayout root,body,nav; private TextView hint; private EditText words,place,time;
    private Spinner stepPicker; private CheckBox useLocation; private WebView map;
    private int tab=0,day=0,selectedStep=0,diaryPage=0; private boolean busy=false,exportLocation=false;
    private LocationManager locator; private LocationListener listener; private Runnable locationTimeout;
    private final ArrayList<JSONObject> steps=new ArrayList<>();
    private String capturePath=""; private Uri captureUri;
    private ModelSettings modelSettings; private DiaryPolish aiClient; private boolean aiRunning;
    private long aiGeneration; private Runnable aiDeadline; private TextView aiStatus;
    private String mapFocus="",diaryFocus=""; private boolean allRecords=false;
    private ScrollView pageScroll;

    @Override public void onCreate(Bundle state){
        super.onCreate(state); getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        try{
            store=new DiaryStore(new File(getFilesDir(),"diary"));
            modelSettings=new ModelSettings(this);
            try(InputStream in=getAssets().open("trip.json")){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);trip=new JSONObject(out.toString("UTF-8"));}
            for(int i=0;i<trip.getJSONArray("days").length();i++){JSONArray a=trip.getJSONArray("days").getJSONObject(i).getJSONArray("steps");for(int j=0;j<a.length();j++)steps.add(a.getJSONObject(j));}
            draft=store.draft();if(draft!=null){draftPhotos=draft.optJSONArray("photos");if(draftPhotos==null)draftPhotos=new JSONArray();capturePath=draft.optString("pendingCapture");}
            if(state!=null){tab=state.getInt("tab");day=state.getInt("day");selectedStep=state.getInt("step");busy=false;} else if(draft!=null)tab=1;
            render();
        }catch(Exception e){LinearLayout error=column();error.setPadding(dp(24),dp(48),dp(24),dp(24));error.addView(text("本机记录暂时无法读取",26,INK));error.addView(text("原文件已保留，没有清空。请勿卸载应用，以免丢失记录。\n"+safe(e),16,MUTED));setContentView(error);}
    }
    @Override protected void onSaveInstanceState(Bundle b){b.putInt("tab",tab);b.putInt("day",day);b.putInt("step",selectedStep);super.onSaveInstanceState(b);}
    @Override protected void onPause(){if(tab==1&&!busy)persistEditorQuietly();super.onPause();}
    @Override protected void onStop(){stopLocation();cancelPolish();super.onStop();}
    @Override protected void onDestroy(){stopLocation();cancelPolish();if(map!=null)map.destroy();worker.shutdown();aiWorker.shutdownNow();super.onDestroy();}
    @Override public void onBackPressed(){if(busy){toast("正在保存，请稍等");return;}if(tab==1){persistEditorQuietly();tab=0;render();}else if(tab!=0){tab=0;render();}else super.onBackPressed();}
    private int dp(float n){return (int)(getResources().getDisplayMetrics().density*n+.5f);}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private TextView text(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setLineSpacing(dp(4),1);t.setPadding(0,dp(6),0,dp(6));return t;}
    private GradientDrawable bg(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private Button button(String s,Runnable action){Button b=new Button(this);b.setText(s);b.setTextSize(15);b.setAllCaps(false);b.setMinHeight(dp(48));b.setTextColor(GREEN);b.setBackground(bg(0xffe6f2f0,12));b.setPadding(dp(10),dp(8),dp(10),dp(8));b.setOnClickListener(v->{if(busy){toast("正在处理，请稍等");return;}action.run();});return b;}
    private void pair(LinearLayout parent,Button a,Button b){LinearLayout r=row();LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(50),1);lp.setMargins(dp(3),dp(5),dp(3),dp(5));r.addView(a,lp);r.addView(b,new LinearLayout.LayoutParams(lp));parent.addView(r);}
    private void gap(LinearLayout l,int h){View v=new View(this);l.addView(v,new LinearLayout.LayoutParams(1,dp(h)));}
    private void divider(LinearLayout l){View v=new View(this);v.setBackgroundColor(0xffdce5df);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(1));p.setMargins(0,dp(14),0,dp(14));l.addView(v,p);}
    private void render(){
        cancelPolish();aiStatus=null;
        if(map!=null){map.destroy();map=null;}words=place=time=null;useLocation=null;
        root=column();root.setBackgroundColor(PAPER);setContentView(root);
        LinearLayout top=row();top.setPadding(dp(20),dp(12),dp(20),dp(4));
        TextView title=text(tab==0?"湘江慢游":tab==1?"记下这一刻":"我的长沙日记",25,INK);title.setTypeface(null,Typeface.BOLD);top.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        top.addView(button(tab==0?"出发前":"说明",this::showPrep));root.addView(top);
        ScrollView scroll=new ScrollView(this);pageScroll=scroll;scroll.setFillViewport(true);body=column();body.setPadding(dp(20),0,dp(20),dp(20));scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        hint=text("",13,MUTED);body.addView(hint);
        if(tab==0)guide();else if(tab==1)editor();else diary();
        nav=row();nav.setPadding(dp(12),dp(7),dp(12),dp(8));nav.setBackgroundColor(PAPER);
        String[] labels={"地图与行程","记一刻","日记"};for(int i=0;i<3;i++){final int n=i;Button b=button(labels[i],()->{if(tab==1&&!persistEditorQuietly())return;tab=n;if(n==1&&draft==null)newDraft();render();});if(i!=tab)b.setBackground(bg(PAPER,10));nav.addView(b,new LinearLayout.LayoutParams(0,dp(52),1));}root.addView(nav);
    }
    private void guide(){
        hint.setText("示例两日行程 · 一天一处，慢慢看");
        body.addView(button("识图模型配置",this::showModelSettings));gap(body,8);
        pair(body,button("第一天  "+trip.optJSONArray("days").optJSONObject(0).optString("theme"),()->{day=0;render();}),button("第二天  "+trip.optJSONArray("days").optJSONObject(1).optString("theme"),()->{day=1;render();}));
        map=new WebView(this);map.setBackgroundColor(0xffeef4f0);map.getSettings().setJavaScriptEnabled(true);map.getSettings().setAllowFileAccess(false);map.getSettings().setAllowContentAccess(false);map.getSettings().setDomStorageEnabled(false);map.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);map.getSettings().setBlockNetworkLoads(true);
        map.setWebViewClient(new WebViewClient(){
            @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r){
                Uri u=r.getUrl();String path=u.getPath();if(!"https".equals(u.getScheme())||!"appassets.xiangjiang.invalid".equals(u.getHost())||u.getQuery()!=null||!"GET".equals(r.getMethod()))return emptyResponse();
                if(path!=null&&path.startsWith("/photo/"))try{String id=path.substring(7);if(!DiaryStore.referenced(store.snapshot()).contains(id))return emptyResponse();return new WebResourceResponse("image/jpeg",null,new FileInputStream(store.photo(id)));}catch(Exception e){return emptyResponse();}
                if(!("/map.html".equals(path)||"/map-data.js".equals(path)))return emptyResponse();
                try{return new WebResourceResponse(path.endsWith("js")?"application/javascript":"text/html","UTF-8",getAssets().open(path.substring(1)));}catch(Exception e){return null;}
            }
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){Uri u=r.getUrl();if(r.isForMainFrame()&&u.getQuery()==null&&u.getFragment()==null&&DiaryMap.recordLink(u.getScheme(),u.getHost(),u.getPath()))openDiaryPoint(u.getPath().substring(1));return true;}
            @Override public void onPageFinished(WebView v,String url){if(v!=map||tab!=0)return;if(location!=null)v.evaluateJavascript("window.setPosition("+location.toString()+")",null);try{String date=allRecords?"":TripDates.date(trip,day);v.evaluateJavascript("window.setRecords("+DiaryMap.points(store.snapshot(),date)+")",null);if(!mapFocus.isEmpty()){v.evaluateJavascript("window.focusRecord("+JSONObject.quote(mapFocus)+")",null);mapFocus="";}}catch(Exception e){toast("记录位置暂时无法显示");}}
        });
        body.addView(map,new LinearLayout.LayoutParams(-1,dp(320)));map.loadUrl("https://appassets.xiangjiang.invalid/map.html");
        body.addView(button(allRecords?"正在显示全部日期记录 · 切回当天":"显示全部日期的记录位置",()->{allRecords=!allRecords;render();}));
        pair(body,button("我在哪",this::locate),button("为这一站记一刻",this::beginRecord));
        if(location!=null)body.addView(text(locationLabel(location),13,MUTED));
        try{
            JSONObject timing=trip.getJSONObject("timing").getJSONObject("days").getJSONObject(day==0?"sat":"sun");
            TextView heading=text(day==0?"示例去程 · 按实际票面填写":"示例返程 · 按实际票面填写",20,GREEN);heading.setTypeface(null,Typeface.BOLD);body.addView(heading);
            JSONArray cps=timing.getJSONArray("checkpoints");for(int i=0;i<cps.length();i++){JSONObject c=cps.getJSONObject(i);body.addView(text(c.getString("time")+"  "+c.getString("label"),17,INK));}
            body.addView(text(timing.getString("note"),14,MUTED));divider(body);
            JSONArray ss=trip.getJSONArray("days").getJSONObject(day).getJSONArray("steps");
            for(int i=0;i<ss.length();i++){
                JSONObject s=ss.getJSONObject(i);LinearLayout block=column();TextView t=text(s.getString("shortTime")+"\n"+s.getString("title"),18,INK);block.addView(t);
                LinearLayout detail=column();detail.setVisibility(View.GONE);JSONArray rows=s.getJSONArray("rows");for(int j=0;j<rows.length();j++){JSONArray r=rows.getJSONArray(j);detail.addView(text(r.getString(0)+"\n"+r.getString(1),15,MUTED));}
                detail.addView(button("在这里记一刻",()->{selectedStep=steps.indexOf(s)+1;beginRecord();}));block.addView(detail);
                t.setPadding(0,dp(12),0,dp(12));t.setContentDescription("展开 "+s.getString("title"));t.setOnClickListener(v->{detail.setVisibility(detail.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE);selectedStep=steps.indexOf(s)+1;if(map!=null)map.evaluateJavascript("window.showStep("+JSONObject.quote(s.optString("view","core"))+","+s.optJSONArray("routes")+")",null);});body.addView(block);divider(body);
            }
            body.addView(text("公开版只含合成示例，按实际票面与官方公告规划。交通是时间预算，不是实时导航。地图标点不代表已到访。",13,MUTED));
        }catch(Exception e){toast(safe(e));}
    }
    private WebResourceResponse emptyResponse(){return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));}
    private void openDiaryPoint(String id){try{ArrayList<JSONObject> records=new ArrayList<>();JSONArray a=store.snapshot().getJSONArray("records");for(int i=0;i<a.length();i++)records.add(a.getJSONObject(i));records.sort(Comparator.comparing(r->r.optString("occurredAt")));for(int i=0;i<records.size();i++)if(records.get(i).optString("id").equals(id)){diaryPage=i/4;diaryFocus=id;tab=2;render();return;}}catch(Exception e){toast("这条记录暂时无法打开");}}
    private void showPrep(){try{
        StringBuilder s=new StringBuilder("记录与照片默认保存在本机。只有你配置服务并逐次确认润色时，才发送所选照片、地点和原文；精确坐标默认不发。服务可能收费并保留素材，请选可信服务。密钥在手机加密保存，不进入日记或备份；私人BYOK试用无法抵御已被破解的手机。\n\n卸载或清除应用数据会丢失记录，请导出备份。ZIP包含记录和精确位置，谨慎保管。照片为压缩副本，不改系统相册原图。旧照片的时间和GPS仅在你确认后采用；地图上的分散记录点不是全天轨迹，也不是到访证明。\n\n");
        JSONArray checks=trip.getJSONArray("checklist");for(int i=0;i<checks.length();i++)s.append("• ").append(checks.getString(i)).append("\n\n");
        new AlertDialog.Builder(this).setTitle("出发前与使用说明").setMessage(s.toString()).setPositiveButton("知道了",null).show();
    }catch(Exception e){toast(safe(e));}}
    private void newDraft(){try{
        draft=new JSONObject().put("id",UUID.randomUUID().toString()).put("createdAt",System.currentTimeMillis()).put("occurredAt",LocalDateTime.now().withSecond(0).withNano(0).toString()).put("timeSource","record-time").put("text","").put("place",selectedStep>0?steps.get(selectedStep-1).optString("title"):"").put("stepId",selectedStep>0?steps.get(selectedStep-1).optString("id"):"").put("photos",new JSONArray());draftPhotos=draft.getJSONArray("photos");store.draft(draft);
    }catch(Exception e){toast(safe(e));}}
    private void beginRecord(){if(draft!=null){new AlertDialog.Builder(this).setTitle("还有一份草稿").setMessage("继续填写，或明确放弃这份草稿后开始新的记录。已保存的日记不受影响。").setNegativeButton("继续草稿",(d,w)->{tab=1;render();}).setPositiveButton("放弃并新建",(d,w)->{newDraft();tab=1;render();}).show();}else{newDraft();tab=1;render();}}
    private EditText input(String value,String placeholder,int max){EditText e=new EditText(this);e.setTextSize(17);e.setTextColor(INK);e.setHint(placeholder);e.setText(value);e.setPadding(dp(12),dp(10),dp(12),dp(10));e.setBackground(bg(0xffedf3ef,10));e.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(max)});return e;}
    private void editor(){
        if(draft==null)newDraft();hint.setText("一句话、一张照片，也可以是一篇游记。草稿保存在本机。");
        pair(body,button("拍照",this::takePhoto),button("从相册选择",this::pickPhoto));
        if(!capturePath.isEmpty())body.addView(button("有未完成拍摄：尝试找回照片",()->{File f=new File(capturePath);if(f.isFile()&&f.length()>0)importPhotos(Collections.singletonList(Uri.fromFile(f)),"camera");else toast("没有可用照片，可重新拍摄");}));
        photoStrip(body,draftPhotos,true);gap(body,8);
        if(draftPhotos.length()>0)body.addView(button("查看照片拍摄时间 / GPS",this::photoMetadata));
        body.addView(text("这一刻发生了什么",16,INK));words=input(draft.optString("text"),"看江、吃饭、走累了……写自己的感受",6000);words.setMinLines(3);words.setGravity(Gravity.TOP);body.addView(words);
        body.addView(text("记录时间（可改）",16,INK));time=input(draft.optString("occurredAt").replace('T',' '),"2025-03-15 14:30",25);time.setSingleLine(true);body.addView(time);body.addView(text("时间来源："+(draft.optString("timeSource").equals("confirmed-photo-exif")?"你已确认采用照片时间；缺时区时按手机本地时间理解":"你填写的记录时间，不自动推断拍摄时刻"),12,MUTED));
        body.addView(text("地点与关联行程",16,INK));place=input(draft.optString("place"),"手写地点，或不填",150);body.addView(place);
        stepPicker=new Spinner(this);ArrayList<String> labels=new ArrayList<>();labels.add("计划外 / 不关联行程");int selection=0;for(int i=0;i<steps.size();i++){labels.add(steps.get(i).optString("title"));if(steps.get(i).optString("id").equals(draft.optString("stepId")))selection=i+1;}
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,labels);stepPicker.setAdapter(adapter);stepPicker.setSelection(selection);body.addView(stepPicker,new LinearLayout.LayoutParams(-1,dp(52)));
        useLocation=new CheckBox(this);useLocation.setTextColor(INK);useLocation.setText("附上记录位置（主动定位或已确认的照片GPS）");useLocation.setChecked(draft.has("location"));body.addView(useLocation);
        if(draft.has("location"))body.addView(text(locationLabel(draft.optJSONObject("location")),13,MUTED));else if(location!=null)body.addView(text(locationLabel(location),13,MUTED));else body.addView(text("尚无定位；可直接手写地点并保存。",13,MUTED));
        pair(body,button("获取当前位置",this::locate),button("保存这一刻",this::saveRecord));
        polishSection();
        body.addView(button("放弃草稿",()->new AlertDialog.Builder(this).setTitle("放弃这份草稿？").setMessage("只移除本机草稿，不删除系统相册照片或已保存的记录。").setNegativeButton("继续写",null).setPositiveButton("放弃",(d,w)->{try{store.clearDraft();draft=null;draftPhotos=new JSONArray();capturePath="";tab=0;render();}catch(Exception e){toast(safe(e));}}).show()));
    }
    private JSONObject readEditor()throws Exception{
        JSONObject value=new JSONObject(draft.toString());String at=time.getText().toString().trim().replace(' ','T');if(!at.equals(value.optString("occurredAt")))value.put("timeSource","user-record-time");value.put("text",words.getText().toString()).put("place",place.getText().toString()).put("occurredAt",at).put("photos",draftPhotos);
        int step=stepPicker.getSelectedItemPosition();value.put("stepId",step>0?steps.get(step-1).optString("id"):"");
        if(useLocation.isChecked()){JSONObject l=value.optJSONObject("location");if(l==null)l=location;if(l==null)throw new IOException("先获取位置，或取消附上位置");value.put("location",l);}else value.remove("location");return value;
    }
    private boolean persistEditorQuietly(){if(words==null||draft==null)return true;try{draft=readEditor();store.draft(draft);return true;}catch(Exception e){toast(safe(e));return false;}}
    private void saveRecord(){try{
        cancelPolish();
        final JSONObject record=readEditor();LocalDateTime.parse(record.getString("occurredAt"));if(record.getString("text").trim().isEmpty()&&record.getJSONArray("photos").length()==0){toast("写一句话或添加照片再保存");return;}
        record.remove("pendingCapture");record.put("updatedAt",System.currentTimeMillis());store.draft(record);busy=true;hint.setText("正在保存照片与文字……");
        worker.execute(()->{try{store.upsert(record);store.clearDraft();runOnUiThread(()->{busy=false;draft=null;draftPhotos=new JSONArray();tab=2;render();toast("已保存在本机");});}catch(Exception e){fail(e);}});
    }catch(Exception e){toast("未保存："+safe(e));}}
    private void photoStrip(LinearLayout parent,JSONArray photos,boolean edit){
        if(photos.length()==0){parent.addView(text("照片会出现在这里，也可以只记文字。",14,MUTED));return;}
        HorizontalScrollView scroll=new HorizontalScrollView(this);LinearLayout strip=row();
        for(int i=0;i<photos.length();i++){final int n=i;try{String id=photos.getJSONObject(i).getString("id");LinearLayout p=column();ImageView image=photoView(id,dp(144),dp(128));p.addView(image);if(edit){p.addView(button(n==0?"首图 · 移除":"设为首图 / 移除",()->new AlertDialog.Builder(this).setItems(new String[]{"设为此条首图","移除此张（不删相册原图）"},(d,w)->{persistEditorQuietly();try{if(w==0){JSONObject photo=draftPhotos.getJSONObject(n);draftPhotos.remove(n);JSONArray reordered=new JSONArray().put(photo);for(int j=0;j<draftPhotos.length();j++)reordered.put(draftPhotos.get(j));draftPhotos=reordered;}else draftPhotos.remove(n);draft.put("photos",draftPhotos);store.draft(draft);render();}catch(Exception e){toast(safe(e));}}).show()));}strip.addView(p);}catch(Exception e){toast(safe(e));}}
        scroll.addView(strip);parent.addView(scroll);
    }
    private ImageView photoView(String id,int width,int height)throws Exception{
        ImageView v=new ImageView(this);v.setLayoutParams(new LinearLayout.LayoutParams(width,height));v.setScaleType(ImageView.ScaleType.CENTER_CROP);BitmapFactory.Options opts=new BitmapFactory.Options();opts.inJustDecodeBounds=true;BitmapFactory.decodeFile(store.photo(id).getPath(),opts);int target=width==-1?Math.min(1200,getResources().getDisplayMetrics().widthPixels):Math.min(480,width);int sample=1;while(opts.outWidth/sample>target||opts.outHeight/sample>target)sample*=2;opts.inJustDecodeBounds=false;opts.inSampleSize=sample;v.setImageBitmap(BitmapFactory.decodeFile(store.photo(id).getPath(),opts));v.setContentDescription("本机旅行照片");return v;
    }
    private void diary(){try{
        JSONObject data=store.snapshot();JSONArray a=data.getJSONArray("records");hint.setText(a.length()+"条实际记录 · 默认本机保存");
        TextView title=text(data.getString("title"),28,INK);title.setTypeface(null,Typeface.BOLD);body.addView(title);title.setOnClickListener(v->{EditText e=input(data.optString("title"),"日记名字",100);new AlertDialog.Builder(this).setTitle("给日记起个名字").setView(e).setNegativeButton("取消",null).setPositiveButton("保存",(d,w)->{try{store.presentation(e.getText().toString(),data.optString("cover"));render();}catch(Exception ex){toast(safe(ex));}}).show();});
        if(!data.optString("cover").isEmpty())body.addView(photoView(data.getString("cover"),-1,dp(230)));
        if(a.length()==0){gap(body,30);body.addView(text("旅行还没有被写下来",22,INK));body.addView(text("从一张照片开始。攻略里的计划不会自动变成你的经历。",17,MUTED));body.addView(button("写第一刻",this::beginRecord));}
        ArrayList<JSONObject> sorted=new ArrayList<>();for(int i=0;i<a.length();i++)sorted.add(a.getJSONObject(i));sorted.sort(Comparator.comparing(r->r.optString("occurredAt")));String date="";
        diaryPage=Math.min(diaryPage,Math.max(0,(sorted.size()-1)/4));
        for(JSONObject r:sorted.subList(diaryPage*4,Math.min(sorted.size(),diaryPage*4+4))){String at=r.getString("occurredAt");if(!date.equals(at.substring(0,10))){date=at.substring(0,10);divider(body);TextView stamp=text(TripDates.heading(trip,date),20,GREEN);stamp.setTypeface(null,Typeface.BOLD);body.addView(stamp);}
            TextView atLabel=text(at.substring(11,16)+"  "+r.optString("place"),15,MUTED);if(r.optString("id").equals(diaryFocus)){atLabel.setText("地图选中的这一刻\n"+atLabel.getText());atLabel.setTextColor(GREEN);atLabel.setTypeface(null,Typeface.BOLD);}body.addView(atLabel);JSONArray photos=r.getJSONArray("photos");if(photos.length()>0){String first=photos.getJSONObject(0).getString("id");ImageView hero=photoView(first,-1,dp(230));hero.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("设为整本日记封面？").setNegativeButton("取消",null).setPositiveButton("设为封面",(d,w)->{try{store.presentation(data.getString("title"),first);render();}catch(Exception e){toast(safe(e));}}).show());body.addView(hero);if(photos.length()>1)photoStrip(body,photos,false);}
            body.addView(text(r.getString("text"),18,INK));
            if(r.optString("id").equals(diaryFocus)){ScrollView target=pageScroll;atLabel.post(()->{if(target==pageScroll&&tab==2)target.smoothScrollTo(0,atLabel.getTop());});}
            if(r.has("ai"))body.addView(text("保留了润色前原文 · 编辑可查看候选稿与恢复原文",12,MUTED));
            if(r.has("location"))body.addView(button("在地图看这一刻",()->{mapFocus=r.optString("id");allRecords=true;tab=0;render();}));else body.addView(text("没有保存坐标，只保留了手写地点",12,MUTED));
            pair(body,button("编辑这一刻",()->editRecord(r)),button("删除记录",()->new AlertDialog.Builder(this).setTitle("删除这一条记录？").setMessage("删除本机日记文字与照片引用，不删除系统相册原图。已导出的备份不受影响。").setNegativeButton("保留",null).setPositiveButton("删除",(d,w)->{try{store.remove(r.getString("id"));render();}catch(Exception e){toast(safe(e));}}).show()));gap(body,14);
        }
        if(sorted.size()>4){body.addView(text("第"+(diaryPage+1)+" / "+((sorted.size()+3)/4)+"页，导出包含全部记录",13,MUTED));pair(body,button("较早的记录",()->{if(diaryPage>0){diaryPage--;render();}}),button("较新的记录",()->{if(diaryPage<(sorted.size()-1)/4){diaryPage++;render();}}));}
        divider(body);pair(body,button("导出含图日记",this::htmlOptions),button("导出完整备份",()->new AlertDialog.Builder(this).setTitle("导出本机备份").setMessage("ZIP包含照片、文字、原图时间/GPS元数据（即使未采用）、已保存坐标和保留的原文/候选稿，但不含模型密钥。备份不加密，请私人保管。保存在你选择的位置；如选择云盘，由你选定的系统服务处理。").setNegativeButton("取消",null).setPositiveButton("选择保存位置",(d,w)->export(BACKUP,"application/zip","湘江慢游-备份.zip")).show()));
        body.addView(button("从备份恢复（合并，不覆盖）",()->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("application/zip").addCategory(Intent.CATEGORY_OPENABLE);launch(i,RESTORE);}));
        body.addView(button("清理已移除的照片副本",()->new AlertDialog.Builder(this).setTitle("清理未使用照片副本？").setMessage("只永久清理App里已不被日记或草稿使用的压缩副本；不会删除系统相册原图。清理后只能从此前导出的ZIP备份找回，请先导出备份。").setNegativeButton("不清理",null).setPositiveButton("清理副本",(d,w)->{try{int n=store.pruneUnused();toast("清理了"+n+"张未使用副本；相册原图未改动");}catch(Exception e){toast(safe(e));}}).show()));
        body.addView(text("点击日记名字可修改；点击照片可设封面。导出只包含已保存记录，草稿请先保存。备份恢复只追加不同ID的记录，已有记录不覆盖。卸载前请导出备份，HTML适合阅读，ZIP用于恢复。",13,MUTED));
    }catch(Exception e){toast(safe(e));}}
    private void editRecord(JSONObject r){Runnable edit=()->{try{draft=new JSONObject(r.toString());draftPhotos=draft.getJSONArray("photos");store.draft(draft);tab=1;render();}catch(Exception e){toast(safe(e));}};if(draft!=null&&!draft.optString("id").equals(r.optString("id")))new AlertDialog.Builder(this).setTitle("放弃现有草稿并编辑这条记录？").setNegativeButton("保留草稿",null).setPositiveButton("放弃并编辑",(d,w)->edit.run()).show();else edit.run();}
    private void htmlOptions(){CheckBox check=new CheckBox(this);check.setText("包含已保存的精确位置（默认不带）");check.setPadding(dp(20),dp(10),dp(20),dp(10));new AlertDialog.Builder(this).setTitle("导出可离线阅读的日记").setView(check).setMessage("所有照片嵌入HTML，文件可能较大。只导出实际记录，不补写没有发生的行程。").setNegativeButton("取消",null).setPositiveButton("选择保存位置",(d,w)->{exportLocation=check.isChecked();getPreferences(0).edit().putBoolean("exportLocation",exportLocation).apply();export(HTML,"text/html","我的长沙日记.html");}).show();}
    private void export(int code,String type,String filename){Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType(type).addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,filename);launch(i,code);}
    private void launch(Intent intent,int code){try{startActivityForResult(intent,code);}catch(ActivityNotFoundException e){toast("没有可用的系统应用，请安装相机或文件选择器");}}
    private void takePhoto(){
        if(draftPhotos.length()>=8){toast("每条最多8张照片");return;}if(!persistEditorQuietly())return;
        try{File dir=new File(getFilesDir(),"capture");if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("相机临时目录不可用");File f=new File(dir,UUID.randomUUID()+".jpg");capturePath=f.getAbsolutePath();draft.put("pendingCapture",capturePath);store.draft(draft);captureUri=FileProvider.getUriForFile(this,getPackageName()+".files",f);
            Intent i=new Intent(MediaStore.ACTION_IMAGE_CAPTURE).putExtra(MediaStore.EXTRA_OUTPUT,captureUri).addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_READ_URI_PERMISSION);i.setClipData(ClipData.newRawUri("photo",captureUri));launch(i,CAMERA);
        }catch(Exception e){toast(safe(e));}
    }
    private void pickPhoto(){if(draftPhotos.length()>=8){toast("每条最多8张照片");return;}if(!persistEditorQuietly())return;
        if(Build.VERSION.SDK_INT>=33){Intent picker=new Intent(MediaStore.ACTION_PICK_IMAGES).setType("image/*");int remaining=8-draftPhotos.length();if(remaining>1)picker.putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX,remaining);if(picker.resolveActivity(getPackageManager())!=null){launch(picker,PICK);return;}}
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*").putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);launch(i,PICK);
    }
    @Override protected void onActivityResult(int code,int result,Intent data){super.onActivityResult(code,result,data);
        if(code==CAMERA){try{if(!capturePath.startsWith(new File(getFilesDir(),"capture").getCanonicalPath()+File.separator))throw new IOException("无效拍摄路径");captureUri=FileProvider.getUriForFile(this,getPackageName()+".files",new File(capturePath));revokeUriPermission(captureUri,Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            if(result==RESULT_OK){importPhotos(Collections.singletonList(Uri.fromFile(new File(capturePath))),"camera");}else{Files.deleteIfExists(new File(capturePath).toPath());capturePath="";if(draft!=null){draft.remove("pendingCapture");store.draft(draft);}toast("拍摄已取消，没有新增记录");render();}}catch(Exception e){toast(safe(e));}return;}
        if(result!=RESULT_OK||data==null||data.getData()==null&&data.getClipData()==null){toast("已取消，原有记录不变");return;}
        if(code==PICK){List<Uri> list=new ArrayList<>();if(data.getClipData()!=null)for(int i=0;i<data.getClipData().getItemCount();i++)list.add(data.getClipData().getItemAt(i).getUri());else list.add(data.getData());importPhotos(list,"album");}
        else if(code==HTML||code==BACKUP){Uri uri=data.getData();busy=true;hint.setText("正在导出，请保留应用打开……");final boolean include=getPreferences(0).getBoolean("exportLocation",false);
            worker.execute(()->{try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new IOException("无法打开导出文件");if(code==HTML)store.html(out,include,trip);else store.backup(out);runOnUiThread(()->{busy=false;render();toast("已导出到选择的位置");});}catch(Exception e){runOnUiThread(()->{busy=false;toast("导出失败，所选文件可能不完整，请重新导出。"+safe(e));});}});
        }else if(code==RESTORE){Uri uri=data.getData();busy=true;worker.execute(()->{try(InputStream in=getContentResolver().openInputStream(uri)){if(in==null)throw new IOException("无法读取备份");File stage=store.inspectBackup(in);try{validateImportedImages(stage);}catch(Exception e){cleanStage(stage);throw e;}int count=new DiaryStore(stage).snapshot().getJSONArray("records").length();runOnUiThread(()->{busy=false;new AlertDialog.Builder(this).setTitle("备份校验通过").setMessage("包含"+count+"条记录。只追加不同ID的记录，同ID以手机现有版本为准，不覆盖。备份中的位置数据会一并恢复。").setNegativeButton("取消",(d,w)->cleanStage(stage)).setOnCancelListener(d->cleanStage(stage)).setPositiveButton("合并恢复",(d,w)->{busy=true;worker.execute(()->{try{int n=store.mergeBackup(stage);store.discardStage(stage);runOnUiThread(()->{busy=false;render();toast("恢复完成，追加"+n+"条记录");});}catch(Exception e){cleanStage(stage);fail(e);}});}).show();});}catch(Exception e){fail(e);}});}
    }
    private void validateImportedImages(File stage)throws Exception{DiaryStore backup=new DiaryStore(stage);for(String id:DiaryStore.referenced(backup.snapshot())){BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(backup.photo(id).getPath(),o);if(o.outWidth<=0||o.outHeight<=0||o.outWidth>2000||o.outHeight>2000||!"image/jpeg".equals(o.outMimeType))throw new IOException("备份照片不是有效的App压缩副本");}}
    private void cleanStage(File stage){try{store.discardStage(stage);}catch(Exception ignored){}}
    private void photoMetadata(){if(!persistEditorQuietly())return;String[] labels=new String[draftPhotos.length()];for(int i=0;i<labels.length;i++)labels[i]="第"+(i+1)+"张照片";new AlertDialog.Builder(this).setTitle("选择要参考的原始照片").setItems(labels,(d,n)->{try{
        JSONObject p=draftPhotos.getJSONObject(n);LinearLayout layout=column();layout.setPadding(dp(20),0,dp(20),0);CheckBox at=new CheckBox(this),gps=new CheckBox(this);
        String original=p.optString("originalTime"),offset=p.optString("originalTimeOffset");at.setText(original.isEmpty()?"原图没有可读取的拍摄时间":"采用拍摄时间 "+original.replace('T',' ')+(offset.isEmpty()?"（无时区，按手机本地理解）":"（原时区 "+offset+"，保留墙上时间）"));at.setEnabled(!original.isEmpty());layout.addView(at);
        gps.setText(p.has("originalLocation")?"采用原图GPS（精度未知，可被原图编辑）":"原图没有可读取的GPS");gps.setEnabled(p.has("originalLocation"));layout.addView(gps);
        new AlertDialog.Builder(this).setTitle("确认采用，不自动修改").setMessage("这里读取的是原图元数据，不是到访证明；压缩副本不携带原EXIF。定位和时间均可继续手动更正。").setView(layout).setNegativeButton("保持现有记录",null).setPositiveButton("采用勾选项",(dialog,w)->{try{if(at.isChecked())draft.put("occurredAt",original).put("timeSource","confirmed-photo-exif");if(gps.isChecked()){JSONObject pos=new JSONObject(p.getJSONObject("originalLocation").toString()).put("accuracy",0).put("capturedAt",p.optLong("importedAt")).put("crs","WGS84").put("source","confirmed-photo-exif");draft.put("location",pos);}store.draft(draft);render();}catch(Exception e){toast(safe(e));}}).show();
    }catch(Exception e){toast("无法读取这张照片的信息");}}).show();}
    private void polishSection(){
        divider(body);body.addView(text("把感想写得更顺",21,GREEN));body.addView(text("你写事实和感受，模型参考所选照片润色。原文会保留，候选稿要你确认；断网也能照常保存日记。",14,MUTED));
        pair(body,button("帮我润色",this::preparePolish),button("模型设置",this::showModelSettings));
        aiStatus=text("不会自动发送素材",13,MUTED);body.addView(aiStatus);body.addView(button("取消正在进行的润色",()->{cancelPolish();if(aiStatus!=null)aiStatus.setText("已停止等待，原文未改。已经发送的素材无法撤回，服务仍可能计费。");}));
        JSONObject ai=draft.optJSONObject("ai");if(ai!=null){
            body.addView(text("润色候选稿（尚可手动修改）",16,INK));body.addView(text(ai.optString("candidate"),18,INK));
            body.addView(text("来自 "+ai.optString("model")+" / "+ai.optString("host")+"；请核对事实。采用后仍需保存这一刻。",12,MUTED));
            pair(body,button("采用这份润色稿",()->applyCandidate(true)),button("恢复润色前原文",()->applyCandidate(false)));
            body.addView(button("查看保留的原文",()->new AlertDialog.Builder(this).setTitle("润色前的原文").setMessage(ai.optString("originalText")).setPositiveButton("关闭",null).show()));
        }
    }
    private void applyCandidate(boolean candidate){if(!persistEditorQuietly())return;
        new AlertDialog.Builder(this).setTitle(candidate?"替换编辑框中的文字？":"恢复润色前原文？").setMessage("会替换当前编辑框的文字，但不改变已保存日记；请再点保存。最初原文仍保留。").setNegativeButton("不替换",null).setPositiveButton("确认",(d,w)->{try{draft=DiaryPolish.adopt(draft,candidate);store.draft(draft);render();}catch(Exception e){toast("文字未替换，请重试");}}).show();
    }
    private void showModelSettings(){if(!persistEditorQuietly())return;cancelPolish();startActivity(new Intent(this,ModelSettingsActivity.class));}
    private void preparePolish(){if(aiRunning){toast("已有一份润色在等待，请先取消");return;}if(!persistEditorQuietly())return;try{
        JSONObject config=modelSettings.read();if(config.optString("endpoint").isEmpty()||config.optString("key").isEmpty()){showModelSettings();return;}DiaryPolish.endpoint(config.getString("endpoint"));DiaryPolish.model(config.getString("model"));if(draft.optString("text").trim().isEmpty()){toast("先写一句自己的感想，不让模型替你编经历");return;}
        final JSONObject consentSource=new JSONObject(draft.toString());LinearLayout layout=column();layout.setPadding(dp(20),0,dp(20),dp(10));layout.addView(text("发往："+config.getString("endpoint")+"\n模型："+config.getString("model")+"\n地点："+consentSource.optString("place")+"\n记录时间："+consentSource.optString("occurredAt")+"\n原文："+consentSource.optString("text"),14,INK));
        ArrayList<CheckBox> picks=new ArrayList<>();for(int i=0;i<draftPhotos.length();i++){JSONObject p=draftPhotos.getJSONObject(i);LinearLayout row=row();row.addView(photoView(p.getString("id"),dp(72),dp(68)));CheckBox c=new CheckBox(this);c.setText("发送第"+(i+1)+"张照片");c.setTextColor(INK);row.addView(c,new LinearLayout.LayoutParams(0,-2,1));layout.addView(row);picks.add(c);}
        CheckBox coords=new CheckBox(this);coords.setText("另外发送精确记录坐标（默认不发）");coords.setEnabled(draft.has("location"));layout.addView(coords);layout.addView(text("最多3张，发送去EXIF的缩小副本。照片可包含人脸或私人信息，确认前请核对。未勾选照片只润色文字。服务可能收费和保存素材；取消无法撤回已发送内容。",13,MUTED));ScrollView scroll=new ScrollView(this);scroll.addView(layout);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("确认本次发送的素材").setView(scroll).setNegativeButton("不发送",null).setPositiveButton("确认发送并润色",null).create();dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{if(!persistEditorQuietly())return;if(!polishInput(consentSource).equals(polishInput(draft))){dialog.dismiss();toast("素材或定位已变化，请重新核对后发送");return;}JSONArray ids=new JSONArray();for(int i=0;i<picks.size();i++)if(picks.get(i).isChecked())ids.put(consentSource.getJSONArray("photos").getJSONObject(i).getString("id"));if(ids.length()>3){toast("一次最多勾选3张照片");return;}dialog.dismiss();startPolish(config,consentSource,ids,coords.isChecked());}catch(Exception e){toast("未发送，请检查素材");}}));dialog.show();
    }catch(Exception e){toast("请先检查模型设置；没有发送素材");}}
    private byte[] aiPhoto(String id)throws Exception{
        BitmapFactory.Options o=new BitmapFactory.Options();o.inSampleSize=2;Bitmap b=BitmapFactory.decodeFile(store.photo(id).getPath(),o);if(b==null)throw new IOException("照片无法读取");Bitmap small=null;
        try{float f=Math.min(1f,1280f/Math.max(b.getWidth(),b.getHeight()));small=Bitmap.createScaledBitmap(b,Math.max(1,Math.round(b.getWidth()*f)),Math.max(1,Math.round(b.getHeight()*f)),true);ByteArrayOutputStream out=new ByteArrayOutputStream();if(!small.compress(Bitmap.CompressFormat.JPEG,78,out)||out.size()>DiaryPolish.MAX_IMAGE_BYTES)throw new IOException("照片过大，请减少素材");return out.toByteArray();}finally{if(small!=null&&small!=b)small.recycle();b.recycle();}
    }
    private String polishInput(JSONObject r)throws Exception{JSONObject copy=new JSONObject(r.toString());copy.remove("ai");return copy.toString();}
    private void startPolish(JSONObject config,JSONObject source,JSONArray ids,boolean coordinates)throws Exception{
        cancelPolish();aiRunning=true;final long generation=aiGeneration;final String input=polishInput(source);final DiaryPolish client=new DiaryPolish();aiClient=client;aiStatus.setText("正在生成候选稿……可取消，也可以直接保存原文");
        aiDeadline=()->{if(aiRunning&&generation==aiGeneration){cancelPolish();if(aiStatus!=null)aiStatus.setText("润色等待已超时。原文未改，可离线保存；重试需再次确认发送。");}};handler.postDelayed(aiDeadline,75000);
        aiWorker.execute(()->{try{
            List<byte[]> images=new ArrayList<>();for(int i=0;i<ids.length();i++)images.add(aiPhoto(ids.getString(i)));JSONObject payload=DiaryPolish.request(config.getString("model"),source,images,coordinates);String result=client.send(config.getString("endpoint"),config.getString("key"),payload);
            runOnUiThread(()->{if(generation!=aiGeneration||!aiRunning||isFinishing()||isDestroyed())return;try{JSONObject current=readEditor();if(!input.equals(polishInput(current))){cancelPolish();aiStatus.setText("等待时素材有修改，本次候选稿未采用；原文未改。请重新确认发送。");return;}JSONObject next=DiaryPolish.candidate(current,result,config.getString("model"),DiaryPolish.endpoint(config.getString("endpoint")).getHost(),ids,coordinates);store.draft(next);draft=next;render();toast("候选稿已生成，核对后再采用");}catch(Exception e){cancelPolish();if(aiStatus!=null)aiStatus.setText("候选稿未保存，原文未改，请重试。");}});
        }catch(Exception e){runOnUiThread(()->{if(generation!=aiGeneration||!aiRunning)return;cancelPolish();if(aiStatus!=null)aiStatus.setText("润色未完成："+(e instanceof IOException&&e.getMessage()!=null&&!e.getMessage().contains(config.optString("key"))&&!e.getMessage().contains("https://")?e.getMessage():"网络或模型接口不可用")+"。原文未改，可离线保存。");});}});
    }
    private void cancelPolish(){if(aiRunning&&aiStatus!=null)aiStatus.setText("已停止等待，原文未改；已经发送的素材无法撤回。");aiGeneration++;aiRunning=false;if(aiDeadline!=null)handler.removeCallbacks(aiDeadline);aiDeadline=null;if(aiClient!=null)aiClient.cancel();aiClient=null;}
    private void importPhotos(List<Uri> uris,String source){
        if(draft==null){toast("找不到记录草稿，请重新打开记一刻");return;}if(draftPhotos.length()+uris.size()>8){toast("每条最多8张；请减少选择数量");return;}
        busy=true;hint.setText("正在复制照片到本机……");final JSONObject current;
        try{current=new JSONObject(draft.toString());}catch(Exception e){busy=false;return;}
        worker.execute(()->{try{
            JSONArray photos=current.getJSONArray("photos");for(Uri uri:uris)photos.put(copyPhoto(uri,source));current.put("photos",photos);current.remove("pendingCapture");store.draft(current);
            if(source.equals("camera")&&!capturePath.isEmpty())Files.deleteIfExists(new File(capturePath).toPath());
            runOnUiThread(()->{busy=false;capturePath="";draft=current;draftPhotos=photos;tab=1;render();toast("照片已加入草稿，记得保存这一刻");});
        }catch(Exception e){fail(e);}});
    }
    private JSONObject copyPhoto(Uri uri,String source)throws Exception{
        if(store.mediaBytes()>=DiaryStore.MEDIA_LIMIT)throw new IOException("本机照片已达240MB上限，请先导出备份");
        File temp=File.createTempFile("import-",".image",getCacheDir());Bitmap decoded=null,oriented=null,scaled=null;
        try{
            try(InputStream in=getContentResolver().openInputStream(uri);FileOutputStream out=new FileOutputStream(temp)){if(in==null)throw new IOException("照片无法读取");byte[] buf=new byte[8192];int n;long bytes=0;while((n=in.read(buf))!=-1){bytes+=n;if(bytes>64L*1024*1024)throw new IOException("原照片超过64MB，请换一张");out.write(buf,0,n);}}
            BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeFile(temp.getPath(),options);if(options.outWidth<=0||options.outHeight<=0)throw new IOException("不支持此图片，请选择JPEG/PNG照片");int sample=1;while(Math.max(options.outWidth,options.outHeight)/sample>2400)sample*=2;options.inSampleSize=sample;options.inJustDecodeBounds=false;decoded=BitmapFactory.decodeFile(temp.getPath(),options);if(decoded==null)throw new IOException("照片解码失败");
            ExifInterface exif=new ExifInterface(temp.getPath());JSONObject metadata=new JSONObject();String exifTime=exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL);if(exifTime!=null)try{metadata.put("originalTime",LocalDateTime.parse(exifTime,DateTimeFormatter.ofPattern("uuuu:MM:dd HH:mm:ss").withResolverStyle(java.time.format.ResolverStyle.STRICT)).withSecond(0).toString());String offset=exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL);if(offset!=null&&offset.length()<=10)metadata.put("originalTimeOffset",offset);}catch(Exception ignored){}
            double[] gps=exif.getLatLong();if(gps!=null&&Double.isFinite(gps[0])&&Double.isFinite(gps[1])&&Math.abs(gps[0])<=90&&Math.abs(gps[1])<=180)metadata.put("originalLocation",new JSONObject().put("latitude",gps[0]).put("longitude",gps[1]));
            int orientation=exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL);Matrix m=new Matrix();switch(orientation){case 2:m.setScale(-1,1);break;case 3:m.setRotate(180);break;case 4:m.setScale(1,-1);break;case 5:m.setRotate(90);m.postScale(-1,1);break;case 6:m.setRotate(90);break;case 7:m.setRotate(270);m.postScale(-1,1);break;case 8:m.setRotate(270);break;}
            oriented=Bitmap.createBitmap(decoded,0,0,decoded.getWidth(),decoded.getHeight(),m,true);float factor=Math.min(1f,2000f/Math.max(oriented.getWidth(),oriented.getHeight()));scaled=Bitmap.createScaledBitmap(oriented,Math.max(1,Math.round(oriented.getWidth()*factor)),Math.max(1,Math.round(oriented.getHeight()*factor)),true);
            ByteArrayOutputStream out=new ByteArrayOutputStream();if(!scaled.compress(Bitmap.CompressFormat.JPEG,86,out))throw new IOException("照片压缩失败");if(store.mediaBytes()+out.size()>DiaryStore.MEDIA_LIMIT)throw new IOException("照片空间已达上限");String id=UUID.randomUUID()+".jpg";DiaryStore.atomic(store.photo(id),out.toByteArray());
            return metadata.put("id",id).put("source",source).put("importedAt",System.currentTimeMillis()).put("width",scaled.getWidth()).put("height",scaled.getHeight()).put("captureTimeSource",metadata.has("originalTime")?"unconfirmed-photo-exif":"unknown-original-time");
        }finally{Files.deleteIfExists(temp.toPath());if(scaled!=null)scaled.recycle();if(oriented!=null&&oriented!=scaled)oriented.recycle();if(decoded!=null&&decoded!=oriented&&decoded!=scaled)decoded.recycle();}
    }
    private void locate(){if(tab==1&&!persistEditorQuietly())return;
        if(checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED&&checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},PERMISSION);return;}startLocation();
    }
    @Override public void onRequestPermissionsResult(int r,String[] permissions,int[] results){super.onRequestPermissionsResult(r,permissions,results);if(r==PERMISSION){if(checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED||checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED)startLocation();else toast("定位未授权，仍可手写地点和记录");}}
    private void startLocation(){
        stopLocation();locator=(LocationManager)getSystemService(LOCATION_SERVICE);hint.setText("正在定位，最多等待20秒；可继续读攻略。");
        listener=new LocationListener(){@Override public void onLocationChanged(Location l){if(l.getAccuracy()>3000)return;try{location=new JSONObject().put("latitude",l.getLatitude()).put("longitude",l.getLongitude()).put("accuracy",l.getAccuracy()).put("capturedAt",l.getTime()).put("crs","WGS84").put("source","foreground-location");stopLocation();
            if(tab==1&&draft!=null){persistEditorQuietly();draft.put("location",location);store.draft(draft);render();}else{hint.setText(locationLabel(location));if(map!=null)map.evaluateJavascript("window.setPosition("+location+")",null);}
        }catch(Exception e){toast(safe(e));}}
        @Override public void onProviderEnabled(String p){}@Override public void onProviderDisabled(String p){}@Override public void onStatusChanged(String p,int s,Bundle b){}
        };try{boolean any=false;for(String p:new String[]{LocationManager.GPS_PROVIDER,LocationManager.NETWORK_PROVIDER})if(locator.isProviderEnabled(p)){try{locator.requestLocationUpdates(p,0,0,listener,Looper.getMainLooper());any=true;}catch(SecurityException ignored){}}if(!any){stopLocation();toast("请开启手机定位，或直接手写地点");return;}
            locationTimeout=()->{stopLocation();if(hint!=null)hint.setText("定位未取得。请到开阔处再试，也可以手写地点。");};handler.postDelayed(locationTimeout,20000);
        }catch(Exception e){stopLocation();toast("定位不可用，仍可手写地点");}
    }
    private void stopLocation(){if(locationTimeout!=null)handler.removeCallbacks(locationTimeout);locationTimeout=null;if(locator!=null&&listener!=null)locator.removeUpdates(listener);listener=null;}
    private String locationLabel(JSONObject l){if(l==null)return "尚无位置";if(l.optString("source").equals("confirmed-photo-exif"))return "你确认采用的照片GPS / 原图精度未知，不自动证明到访。";return "定位精度约"+Math.round(l.optDouble("accuracy"))+"米 / "+DateTimeFormatter.ofPattern("HH:mm:ss").format(Instant.ofEpochMilli(l.optLong("capturedAt")).atZone(ZoneId.systemDefault()))+"获取；不会自动标记到访。";}
    private String safe(Exception e){String s=e.getMessage();return s==null?"操作失败，请重试":s.replace(getFilesDir().getPath(),"本机目录");}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private void fail(Exception e){runOnUiThread(()->{busy=false;if(hint!=null)hint.setText("未完成："+safe(e));toast("未完成："+safe(e)+"；已有记录未清空");});}
}
