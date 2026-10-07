package com.bytewatcher.xiangjiang;

import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Only private app files. A record commit never deletes photo bytes. */
public final class DiaryStore {
    public static final long MEDIA_LIMIT = 240L * 1024 * 1024;
    public static final int MAX_RECORDS = 1000;
    final File root, photos;
    private JSONObject state;
    public DiaryStore(File directory) throws Exception {
        root = directory; photos = new File(root, "photos");
        if (!photos.isDirectory() && !photos.mkdirs()) throw new IOException("无法创建照片目录");
        File file = new File(root, "diary.json");
        state = file.exists() ? new JSONObject(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)) : empty();
        validate(state, true);
    }
    static JSONObject empty() throws JSONException {
        return new JSONObject().put("version", 1).put("title", "在长沙，慢慢走").put("cover", "").put("records", new JSONArray());
    }
    public synchronized JSONObject snapshot() throws JSONException { return new JSONObject(state.toString()); }
    static boolean mediaId(String value) { return value.matches("[a-f0-9\\-]{36}\\.jpg"); }
    public File photo(String id) throws IOException {
        if (!mediaId(id)) throw new IOException("无效照片引用");
        return new File(photos, id);
    }
    public synchronized void upsert(JSONObject record) throws Exception {
        JSONObject next = snapshot(); JSONArray list = next.getJSONArray("records");
        boolean found = false;
        for (int i=0; i<list.length(); i++) if (list.getJSONObject(i).getString("id").equals(record.getString("id"))) { list.put(i, record); found = true; break; }
        if (!found) list.put(record);
        commit(next);
    }
    public synchronized void remove(String id) throws Exception {
        JSONObject next = snapshot(); JSONArray list = next.getJSONArray("records");
        for (int i=0;i<list.length();i++) if(list.getJSONObject(i).getString("id").equals(id)) { list.remove(i); break; }
        if (!referenced(next).contains(next.optString("cover"))) next.put("cover", "");
        commit(next); // Keep private media to make deletion recoverable via pre-deletion backup.
    }
    public synchronized void presentation(String title, String cover) throws Exception {
        JSONObject next=snapshot(); next.put("title", title).put("cover", cover); commit(next);
    }
    void commit(JSONObject next) throws Exception { validate(next, true); atomic(new File(root,"diary.json"),next.toString().getBytes(StandardCharsets.UTF_8)); state=next; }
    public synchronized void draft(JSONObject draft) throws Exception { atomic(new File(root,"draft.json"),draft.toString().getBytes(StandardCharsets.UTF_8)); }
    public synchronized JSONObject draft() throws Exception {
        File file=new File(root,"draft.json"); return file.exists()?new JSONObject(new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8)):null;
    }
    public void clearDraft() throws Exception { Files.deleteIfExists(new File(root,"draft.json").toPath()); }
    public long mediaBytes() { File[] files=photos.listFiles(); long n=0; if(files!=null)for(File f:files)n+=f.length(); return n; }
    public synchronized int pruneUnused() throws Exception {
        Set<String> used=referenced(state);JSONObject d=draft();if(d!=null){JSONArray p=d.optJSONArray("photos");if(p!=null)for(int i=0;i<p.length();i++)used.add(p.getJSONObject(i).getString("id"));}
        int n=0;File[] files=photos.listFiles();if(files!=null)for(File f:files)if(mediaId(f.getName())&&!used.contains(f.getName())){Files.delete(f.toPath());n++;}return n;
    }
    static void atomic(File target, byte[] bytes) throws IOException {
        File temp=new File(target.getParentFile(),target.getName()+".pending");
        try(FileOutputStream out=new FileOutputStream(temp)){ out.write(bytes); out.getFD().sync(); }
        Files.move(temp.toPath(),target.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
    }
    void validate(JSONObject data, boolean files) throws Exception {
        if(data.getInt("version")!=1)throw new IOException("备份版本不支持");
        if(data.getString("title").length()>100)throw new IOException("日记标题过长");
        JSONArray records=data.getJSONArray("records"); if(records.length()>MAX_RECORDS)throw new IOException("最多1000条记录");
        HashSet<String> ids=new HashSet<>();
        for(int i=0;i<records.length();i++){
            JSONObject r=records.getJSONObject(i); UUID.fromString(r.getString("id"));
            if(!ids.add(r.getString("id")))throw new IOException("重复记录");
            if(r.getString("text").length()>6000 || r.getString("place").length()>150)throw new IOException("文字超出限制");
            if(r.has("ai")){
                JSONObject ai=r.getJSONObject("ai");Set<String> allowed=new HashSet<>(Arrays.asList("originalText","inputText","candidate","model","host","generatedAt","photoIds","includeCoordinates","adopted"));
                for(Iterator<String> keys=ai.keys();keys.hasNext();)if(!allowed.contains(keys.next()))throw new IOException("润色数据包含不支持字段");
                for(String k:new String[]{"originalText","inputText","candidate"})if(ai.getString(k).length()>6000)throw new IOException("润色文字过长");
                if(ai.getString("model").length()>120||ai.getString("host").length()>253||ai.getLong("generatedAt")<0||ai.getJSONArray("photoIds").length()>3)throw new IOException("润色来源无效");
                ai.getBoolean("includeCoordinates");ai.getBoolean("adopted");for(int j=0;j<ai.getJSONArray("photoIds").length();j++)if(!mediaId(ai.getJSONArray("photoIds").getString(j)))throw new IOException("润色照片引用无效");
            }
            if(!r.getString("occurredAt").matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}(:\\d{2})?"))throw new IOException("时间格式应为2025-03-15 14:30");
            java.time.LocalDateTime.parse(r.getString("occurredAt"));
            JSONArray p=r.getJSONArray("photos"); if(p.length()>8)throw new IOException("每条最多8张照片");
            for(int j=0;j<p.length();j++){
                String id=p.getJSONObject(j).getString("id"); File f=photo(id);
                JSONObject metadata=p.getJSONObject(j);if(metadata.has("originalTime"))java.time.LocalDateTime.parse(metadata.getString("originalTime"));
                if(metadata.optString("originalTimeOffset").length()>10)throw new IOException("照片时区字段无效");
                if(metadata.has("originalLocation")){JSONObject gps=metadata.getJSONObject("originalLocation");double lat=gps.getDouble("latitude"),lon=gps.getDouble("longitude");if(!Double.isFinite(lat)||!Double.isFinite(lon)||Math.abs(lat)>90||Math.abs(lon)>180)throw new IOException("照片GPS无效");}
                if(files && (!f.isFile() || f.length()<4 || f.length()>12*1024*1024))throw new IOException("照片缺失或过大，请从备份恢复");
                if(files)try(RandomAccessFile media=new RandomAccessFile(f,"r")){if(media.readUnsignedShort()!=0xffd8)throw new IOException("照片不是JPEG");media.seek(media.length()-2);if(media.readUnsignedShort()!=0xffd9)throw new IOException("照片数据不完整");}
            }
            if(r.has("location")){
                JSONObject l=r.getJSONObject("location"); double lat=l.getDouble("latitude"),lon=l.getDouble("longitude");
                if(!Double.isFinite(lat)||!Double.isFinite(lon)||Math.abs(lat)>90||Math.abs(lon)>180||!Double.isFinite(l.getDouble("accuracy"))||l.getDouble("accuracy")<0)throw new IOException("定位数据无效");
            }
        }
        String cover=data.optString("cover"); if(!cover.isEmpty()&&!referenced(data).contains(cover))throw new IOException("封面照片不存在");
    }
    static Set<String> referenced(JSONObject data) throws JSONException {
        Set<String> ids=new LinkedHashSet<>(); JSONArray list=data.getJSONArray("records");
        for(int i=0;i<list.length();i++){ JSONArray p=list.getJSONObject(i).getJSONArray("photos");for(int j=0;j<p.length();j++)ids.add(p.getJSONObject(j).getString("id")); }return ids;
    }
    static String escape(String s){return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");}
    public synchronized void html(OutputStream stream, boolean exactLocation) throws Exception { html(stream, exactLocation, null); }
    /** Offline keepsake page. Headings use the trip calendar when given; plans never become records. */
    public synchronized void html(OutputStream stream, boolean exactLocation, JSONObject trip) throws Exception {
        JSONObject data=snapshot(); ArrayList<JSONObject> sorted=new ArrayList<>(); JSONArray list=data.getJSONArray("records");
        for(int i=0;i<list.length();i++)sorted.add(list.getJSONObject(i)); sorted.sort(Comparator.comparing(r->r.optString("occurredAt")));
        int photoCount=0;Set<String> days=new LinkedHashSet<>();for(JSONObject r:sorted){photoCount+=r.getJSONArray("photos").length();days.add(r.getString("occurredAt").substring(0,10));}
        Writer out=new OutputStreamWriter(stream,StandardCharsets.UTF_8);
        out.write("<!doctype html><html lang='zh-CN'><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'><meta http-equiv='Content-Security-Policy' content=\"default-src 'none'; img-src data:; style-src 'unsafe-inline'\"><title>"+escape(data.getString("title"))+"</title><style>"
            +":root{--ink:#233934;--river:#126b63;--paper:#f7f4ec;--card:#fffdf8;--muted:#7a857f;--line:#e4ddcc}*{box-sizing:border-box}"
            +"body{margin:0;background:var(--paper);color:var(--ink);font:17px/1.8 'Songti SC','Noto Serif SC',serif}main{max-width:720px;margin:0 auto;padding:36px 20px 60px}"
            +"header{text-align:center;padding:12px 0 24px}header .route{letter-spacing:.3em;color:var(--river);font:600 13px/1 sans-serif}h1{font-size:34px;margin:14px 0 6px;letter-spacing:.06em}"
            +".meta{color:var(--muted);font:14px/1.6 sans-serif}.cover{margin:8px 0 28px}.cover img{width:100%;border-radius:4px;box-shadow:0 12px 30px rgba(35,57,52,.18)}"
            +"section{margin-top:40px}.stamp{display:flex;align-items:center;gap:12px;margin:0 0 18px;color:var(--river);font:700 18px/1.4 sans-serif}.stamp:after{content:'';flex:1;height:1px;background:var(--line)}"
            +".stamp b{display:inline-block;border:2px solid var(--river);border-radius:6px;padding:2px 10px;transform:rotate(-2deg)}"
            +"article{background:var(--card);border:1px solid var(--line);border-radius:14px;padding:18px 20px;margin:0 0 22px;box-shadow:0 2px 0 var(--line)}"
            +"article .when{font:13px/1.4 sans-serif;color:var(--muted)}article .when strong{color:var(--river);font-size:15px;margin-right:8px}article p{white-space:pre-wrap;margin:10px 0 4px}"
            +".photos{display:grid;grid-template-columns:repeat(auto-fill,minmax(200px,1fr));gap:8px;margin-top:12px}.photos.one{grid-template-columns:1fr}.photos img{width:100%;height:100%;object-fit:cover;border-radius:8px;aspect-ratio:4/3}.photos.one img{aspect-ratio:auto}"
            +".loc{display:block;margin-top:8px;font:12px/1.4 sans-serif;color:var(--muted)}footer{margin-top:48px;text-align:center;color:var(--muted);font:13px/1.6 sans-serif}"
            +"</style><main><header><div class='route'>武汉 · 长沙 · 两日慢游</div><h1>"+escape(data.getString("title"))+"</h1><div class='meta'>"+days.size()+" 天 · "+sorted.size()+" 个时刻 · "+photoCount+" 张照片<br>下面是实际记录，不是计划打卡。</div></header>");
        String cover=data.optString("cover"); if(!cover.isEmpty()){out.write("<div class='cover'>");image(out,cover);out.write("</div>");}
        String date="";boolean open=false;
        for(JSONObject r:sorted){String t=r.getString("occurredAt");if(!date.equals(t.substring(0,10))){date=t.substring(0,10);if(open)out.write("</section>");out.write("<section><h2 class='stamp'><b>"+escape(TripDates.heading(trip,date))+"</b></h2>");open=true;}
            out.write("<article><div class='when'><strong>"+escape(t.substring(11,16))+"</strong>"+escape(r.getString("place"))+"</div><p>"+escape(r.getString("text"))+"</p>");
            JSONArray p=r.getJSONArray("photos");if(p.length()>0){out.write("<div class='photos"+(p.length()==1?" one":"")+"'>");for(int j=0;j<p.length();j++)image(out,p.getJSONObject(j).getString("id"));out.write("</div>");}
            if(exactLocation&&r.has("location"))out.write("<small class='loc'>记录位置 WGS84: "+escape(r.getJSONObject("location").toString())+"</small>");
            out.write("</article>");
        }if(open)out.write("</section>");out.write("<footer>照片和文字均包含在此文件中，可离线阅读。<br>湘江慢游 · 本机日记</footer></main></html>");out.flush();
    }
    void image(Writer out,String id)throws Exception{out.write("<img alt='旅行照片' src='data:image/jpeg;base64,");out.write(Base64.getEncoder().encodeToString(Files.readAllBytes(photo(id).toPath())));out.write("'>");}
    public synchronized void backup(OutputStream stream) throws Exception {
        try(ZipOutputStream zip=new ZipOutputStream(stream)){
            zip.putNextEntry(new ZipEntry("diary.json"));zip.write(state.toString().getBytes(StandardCharsets.UTF_8));zip.closeEntry();
            for(String id:referenced(state)){zip.putNextEntry(new ZipEntry("photos/"+id));Files.copy(photo(id).toPath(),zip);zip.closeEntry();}
        }
    }
    /** Fully validate in an isolated temporary directory before any live record write. */
    public File inspectBackup(InputStream input) throws Exception {
        File stage=Files.createTempDirectory(root.toPath(),"restore-").toFile();new File(stage,"photos").mkdir();
        long total=0;Set<String> names=new HashSet<>();
        try(ZipInputStream zip=new ZipInputStream(input)){
            ZipEntry e;while((e=zip.getNextEntry())!=null){String name=e.getName();
                if(!names.add(name)||names.size()>8001|| !(name.equals("diary.json")||name.startsWith("photos/")&&mediaId(name.substring(7))))throw new IOException("备份包含不安全或重复路径");
                ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;long size=0;
                while((n=zip.read(buf))!=-1){size+=n;total+=n;if(size>12*1024*1024||total>MEDIA_LIMIT)throw new IOException("备份过大");out.write(buf,0,n);}
                atomic(new File(stage,name),out.toByteArray());zip.closeEntry();
            }
            if(!new File(stage,"diary.json").isFile())throw new IOException("没有日记数据");
            new DiaryStore(stage); return stage;
        }catch(Exception e){discardStage(stage);throw e;}
    }
    public synchronized int mergeBackup(File stage) throws Exception {
        DiaryStore other=new DiaryStore(stage);JSONObject next=snapshot();JSONArray dst=next.getJSONArray("records"),src=other.state.getJSONArray("records");
        Set<String> ids=new HashSet<>();for(int i=0;i<dst.length();i++)ids.add(dst.getJSONObject(i).getString("id"));
        Set<String> needed=new LinkedHashSet<>();int count=0;
        for(int i=0;i<src.length();i++){JSONObject r=src.getJSONObject(i);if(ids.add(r.getString("id"))){dst.put(r);count++;JSONArray p=r.getJSONArray("photos");for(int j=0;j<p.length();j++)needed.add(p.getJSONObject(j).getString("id"));}}
        long bytes=mediaBytes();for(String id:needed)bytes+=other.photo(id).length();if(bytes>MEDIA_LIMIT)throw new IOException("照片已达240MB上限");
        for(String id:needed){File f=photo(id);if(f.exists()){if(!Arrays.equals(Files.readAllBytes(f.toPath()),Files.readAllBytes(other.photo(id).toPath())))throw new IOException("照片ID冲突，未覆盖原照片");}else atomic(f,Files.readAllBytes(other.photo(id).toPath()));}
        commit(next);return count;
    }
    public void discardStage(File stage) throws IOException {
        if(!stage.getCanonicalFile().getParentFile().equals(root.getCanonicalFile())||!stage.getName().startsWith("restore-"))throw new IOException("禁止删除此路径");
        File[] files=new File(stage,"photos").listFiles();if(files!=null)for(File f:files)Files.deleteIfExists(f.toPath());
        Files.deleteIfExists(new File(stage,"photos").toPath());Files.deleteIfExists(new File(stage,"diary.json").toPath());Files.deleteIfExists(stage.toPath());
    }
}
