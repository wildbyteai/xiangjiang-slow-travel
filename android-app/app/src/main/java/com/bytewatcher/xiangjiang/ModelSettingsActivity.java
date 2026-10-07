package com.bytewatcher.xiangjiang;

import android.app.*;
import android.os.Bundle;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.*;
import android.text.method.PasswordTransformationMethod;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import org.json.JSONObject;

/** Native, private configuration page. Saving never makes a model request. */
public final class ModelSettingsActivity extends Activity {
    private static final int PAPER=0xfffafcfb,GREEN=0xff126b63,INK=0xff233934,MUTED=0xff6a7b75;
    private ModelSettings settings; private JSONObject saved;
    private EditText address,model,key; private TextView status; private Button save;
    private boolean unreadable;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        settings=new ModelSettings(this);load();build();
    }
    private void load(){try{saved=settings.read();unreadable=false;}catch(Exception e){saved=new JSONObject();unreadable=true;}}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private TextView label(String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setLineSpacing(dp(4),1);return t;}
    private GradientDrawable background(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(10));return d;}
    private void space(LinearLayout body,int height){body.addView(new View(this),new LinearLayout.LayoutParams(1,dp(height)));}
    private Button button(String title,Runnable action){Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setTextSize(16);b.setTextColor(GREEN);b.setMinHeight(dp(48));b.setBackground(background(0xffe6f2f0));b.setOnClickListener(v->action.run());return b;}
    private EditText field(String value,String hint,int length,boolean single){EditText e=new EditText(this);e.setId(View.generateViewId());e.setTextSize(17);e.setTextColor(INK);e.setHintTextColor(MUTED);e.setHint(hint);e.setText(value);e.setPadding(dp(12),dp(12),dp(12),dp(12));e.setBackground(background(0xffedf3ef));e.setFilters(new InputFilter[]{new InputFilter.LengthFilter(length)});e.setSingleLine(single);e.setSaveEnabled(false);e.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);return e;}
    private void build(){
        LinearLayout root=column();root.setBackgroundColor(PAPER);root.setPadding(dp(20),dp(14),dp(20),0);setContentView(root);
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);Button back=button("返回",this::leave);head.addView(back,new LinearLayout.LayoutParams(dp(68),dp(48)));TextView title=label("识图模型配置",25,INK);title.setTypeface(null,Typeface.BOLD);title.setPadding(dp(12),0,0,0);head.addView(title,new LinearLayout.LayoutParams(0,-2,1));root.addView(head);space(root,18);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);LinearLayout body=column();body.setPadding(0,0,0,dp(28));scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        status=label("",15,GREEN);status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);body.addView(status);space(body,10);body.addView(label("让模型参考照片，润色你写的旅行感想。这里只保存连接信息，不会发送照片或调用模型。",15,MUTED));space(body,24);
        TextView addressLabel=label("服务地址",17,INK);body.addView(addressLabel);space(body,8);address=field(saved.optString("endpoint"),"https://你的服务地址/v1/chat/completions",1000,false);address.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);address.setMinLines(2);addressLabel.setLabelFor(address.getId());body.addView(address);space(body,6);body.addView(label("粘贴服务商提供的完整接口地址，结尾是 /chat/completions。这里只支持 Chat Completions 兼容接口，不是控制台网址。",13,MUTED));space(body,22);
        TextView modelLabel=label("识图模型名",17,INK);body.addView(modelLabel);space(body,8);model=field(saved.optString("model"),"例如 qwen3-vl-plus",120,true);modelLabel.setLabelFor(model.getId());body.addView(model);space(body,6);body.addView(label("必须支持图片输入。模型名按你使用的服务填写；不同服务可能使用不同名称。",13,MUTED));space(body,22);
        TextView keyLabel=label("API 密钥",17,INK);body.addView(keyLabel);space(body,8);key=field("",saved.optString("key").isEmpty()?"在这里填写 API Key":"已保存密钥；留空保留（不回显）",4096,true);key.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);key.setTransformationMethod(PasswordTransformationMethod.getInstance());key.setFreezesText(false);keyLabel.setLabelFor(key.getId());body.addView(key);space(body,6);body.addView(label("密钥只在手机加密保存，不进入安装包、日记或备份。换服务域名或端口时，必须填写该服务的密钥。不要把密钥放进地址。",13,MUTED));space(body,24);
        save=button("保存配置到手机",this::saveSettings);save.setTextColor(PAPER);save.setBackground(background(GREEN));body.addView(save,new LinearLayout.LayoutParams(-1,dp(52)));space(body,12);body.addView(button("返回旅行 App",this::leave),new LinearLayout.LayoutParams(-1,dp(48)));space(body,20);
        body.addView(label("保存成功 ≠ 模型连接成功",16,INK));space(body,6);body.addView(label("保存后回到“记一刻”，写感想、点“帮我润色”，再核对并确认发送。每次选图和发送都由你决定；精确坐标默认不发。服务可能收费并留存素材，请选择可信服务。",13,MUTED));space(body,20);
        body.addView(button("清除手机内的模型配置",this::clearSettings),new LinearLayout.LayoutParams(-1,dp(48)));space(body,8);body.addView(label("这只清除本机配置，不删除旅行记录，也不撤销服务商处的密钥。被破解的手机无法保证密钥安全。此页禁止系统截图；未保存的输入在退出或进程结束后不会保留。",12,MUTED));
        updateStatus();TextWatcher watcher=new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){if(!unreadable){status.setText(dirty()?"有未保存的修改；尚未请求模型":saved.optString("endpoint").isEmpty()?"尚未配置模型":"已保存到手机；接口尚未验证");}}public void afterTextChanged(Editable e){}};address.addTextChangedListener(watcher);model.addTextChangedListener(watcher);key.addTextChangedListener(watcher);
    }
    private boolean dirty(){return ModelSettingsForm.changed(address.getText().toString(),model.getText().toString(),key.getText().toString(),saved);}
    private void updateStatus(){save.setEnabled(!unreadable);status.setText(unreadable?"配置读取失败；旧文件已保留。请清除配置后重新填写，日记不受影响。":saved.optString("endpoint").isEmpty()?"尚未配置模型":"已保存到手机；接口尚未验证");}
    private void saveSettings(){
        address.setError(null);model.setError(null);key.setError(null);boolean written=false;
        try{
            JSONObject next=ModelSettingsForm.prepare(address.getText().toString(),model.getText().toString(),key.getText().toString(),saved);
            settings.save(next.getString("endpoint"),next.getString("model"),next.getString("key"));written=true;
            JSONObject checked=settings.read();for(String name:new String[]{"endpoint","model","key"})if(!checked.getString(name).equals(next.getString(name)))throw new Exception();
            saved=checked;address.setText(saved.getString("endpoint"));model.setText(saved.getString("model"));key.setText("");unreadable=false;updateStatus();status.setText("配置已加密保存并回读；尚未请求或验证模型接口");
            ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(key.getWindowToken(),0);Toast.makeText(this,"配置已保存，没有请求模型",Toast.LENGTH_LONG).show();
        }catch(ModelSettingsForm.Invalid e){EditText target="endpoint".equals(e.field)?address:"model".equals(e.field)?model:key;target.setError(e.getMessage());target.requestFocus();status.setText("配置未保存，请更正提示的字段；没有请求模型");}
        catch(Exception e){status.setText(written?"保存结果未确认，请重新打开本页检查；没有请求模型":"配置未保存，本机写入失败；原配置与日记未清空");}
    }
    private void clearSettings(){new AlertDialog.Builder(this).setTitle("清除手机内的模型配置？").setMessage("删除本机保存的服务地址、模型名和密钥，未保存输入也会清除。旅行记录不受影响；服务商处的密钥不会被撤销。").setNegativeButton("保留",null).setPositiveButton("清除配置",(d,w)->{try{settings.clear();JSONObject next=settings.read();if(!next.optString("endpoint").isEmpty()||!next.optString("key").isEmpty())throw new Exception();saved=next;unreadable=false;address.setText("");model.setText("");key.setText("");updateStatus();status.setText("本机配置已清除，旅行记录未改动");}catch(Exception e){status.setText("清除结果未确认，请重新打开本页检查");}}).show();}
    private void leave(){if(dirty())new AlertDialog.Builder(this).setTitle("配置还没有保存").setMessage("退出将放弃当前输入；手机内已保存的配置和日记不变。").setNegativeButton("继续填写",null).setPositiveButton("放弃输入并返回",(d,w)->finish()).show();else finish();}
    @Override public void onBackPressed(){leave();}
    @Override protected void onDestroy(){if(key!=null)key.setText("");if(saved!=null)saved.remove("key");super.onDestroy();}
}
