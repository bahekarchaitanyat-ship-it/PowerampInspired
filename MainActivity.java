package com.example.powerampinspired;

import android.Manifest;
import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.*;
import android.media.*;
import android.net.Uri;
import android.provider.MediaStore;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.io.*;

public class MainActivity extends Activity {
    private final ArrayList<Track> tracks = new ArrayList<>();
    private MediaPlayer player;
    private Equalizer equalizer;
    private BassBoost bassBoost;
    private Virtualizer virtualizer;
    private LinearLayout root, content, queue;
    private TextView title, artist, elapsed, duration, status;
    private SeekBar seek;
    private Button playButton;
    private int current = -1;
    private boolean userSeeking = false;
    private Handler handler = new Handler();
    private Runnable ticker;
    private final ArrayList<Integer> favorites = new ArrayList<>();
    private final ArrayList<String> playlistNames = new ArrayList<>();
    private final HashMap<String, ArrayList<Integer>> playlists = new HashMap<>();

    static class Track {
        long id; String name, artist, album, path; long duration;
        Track(long i, String n, String a, String al, String p, long d) {
            id=i; name=n; artist=a; album=al; path=p; duration=d;
        }
        Uri uri() { return Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, String.valueOf(id)); }
    }

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        buildShell();
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Build.VERSION.SDK_INT >= 33 ?
                Manifest.permission.READ_MEDIA_AUDIO : Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_AUDIO : Manifest.permission.READ_EXTERNAL_STORAGE}, 7);
        } else scan();
        ticker = new Runnable() { public void run() {
            if (player != null && current >= 0) {
                if (!userSeeking) seek.setProgress(player.getCurrentPosition());
                elapsed.setText(time(player.getCurrentPosition()));
                if (!player.isPlaying() && player.getCurrentPosition() >= player.getDuration()-400) next();
            }
            handler.postDelayed(this, 500);
        }};
        handler.post(ticker);
    }

    private int dp(float n) { return (int)(n*getResources().getDisplayMetrics().density+.5f); }
    private TextView text(String s, int size, int color) {
        TextView t = new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(color); return t;
    }
    private void buildShell() {
        root = new LinearLayout(this); root.setOrientation(1); root.setPadding(dp(18),dp(18),dp(18),dp(12));
        root.setBackgroundColor(Color.rgb(16,17,20)); setContentView(root);
        TextView brand = text("POWERAMP-INSPIRED",13,0xffd6a84f); brand.setLetterSpacing(.18f); root.addView(brand);
        TextView heading = text("Your music",29,Color.WHITE); heading.setPadding(0,dp(8),0,dp(14)); root.addView(heading);
        LinearLayout tabs = new LinearLayout(this);
        String[] names={"SONGS","ALBUMS","ARTISTS","FOLDERS","PLAYLISTS","EQUALIZER"};
        for(String n:names){ Button b=button(n); tabs.addView(b,new LinearLayout.LayoutParams(0,dp(42),1)); b.setOnClickListener(v->showTab(n)); }
        HorizontalScrollView hsv=new HorizontalScrollView(this); hsv.setHorizontalScrollBarEnabled(false); hsv.addView(tabs); root.addView(hsv);
        status=text("Scanning your offline library…",13,0xffa8a8ad); status.setPadding(0,dp(12),0,dp(6)); root.addView(status);
        ScrollView sc=new ScrollView(this); content=new LinearLayout(this); content.setOrientation(1); sc.addView(content);
        root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
        View line=new View(this); line.setBackgroundColor(0xff33343a); root.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));
        title=text("Nothing playing",17,Color.WHITE); artist=text("Choose a song from your library",12,0xffa8a8ad);
        root.addView(title); root.addView(artist);
        seek=new SeekBar(this); seek.setMax(1000); root.addView(seek);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onStartTrackingTouch(SeekBar s){userSeeking=true;}
            public void onStopTrackingTouch(SeekBar s){if(player!=null)player.seekTo(s.getProgress());userSeeking=false;}
            public void onProgressChanged(SeekBar s,int p,boolean u){if(u&&player!=null)elapsed.setText(time(p));}
        });
        LinearLayout times=new LinearLayout(this); elapsed=text("0:00",11,0xffaaaaaa); duration=text("0:00",11,0xffaaaaaa);
        times.addView(elapsed,new LinearLayout.LayoutParams(0,-2,1)); times.addView(duration); root.addView(times);
        LinearLayout controls=new LinearLayout(this); controls.setGravity(Gravity.CENTER);
        Button prev=button("|◀"); playButton=button("▶"); Button next=button("▶|"); Button fav=button("♡");
        controls.addView(prev); controls.addView(playButton); controls.addView(next); controls.addView(fav); root.addView(controls);
        prev.setOnClickListener(v->previous()); next.setOnClickListener(v->next());
        playButton.setOnClickListener(v->{if(player==null){if(!tracks.isEmpty())play(0);}else if(player.isPlaying()){player.pause();playButton.setText("▶");}else{player.start();playButton.setText("Ⅱ");}});
        fav.setOnClickListener(v->{if(current>=0){if(favorites.contains(current)){favorites.remove((Integer)current);fav.setText("♡");}else{favorites.add(current);fav.setText("♥");}}});
        Button shuffle=button("SHUFFLE"); shuffle.setOnClickListener(v->{if(!tracks.isEmpty())play(new Random().nextInt(tracks.size()));});
        root.addView(shuffle);
    }
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setTextSize(11);b.setTextColor(0xffe7e7e9);b.setAllCaps(false);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff292a30));return b;}
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(r==7&&g.length>0&&g[0]==0)scan();else status.setText("Storage permission is needed to scan local audio.");}
    private void scan(){
        tracks.clear();
        Uri uri=MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String[] cols={MediaStore.Audio.Media._ID,MediaStore.Audio.Media.TITLE,MediaStore.Audio.Media.ARTIST,MediaStore.Audio.Media.ALBUM,MediaStore.Audio.Media.DATA,MediaStore.Audio.Media.DURATION};
        try(Cursor c=getContentResolver().query(uri,cols,MediaStore.Audio.Media.IS_MUSIC+"!=0",null,MediaStore.Audio.Media.TITLE+" COLLATE NOCASE ASC")){
            if(c!=null)while(c.moveToNext())tracks.add(new Track(c.getLong(0),c.getString(1),c.getString(2),c.getString(3),c.getString(4),c.getLong(5)));
        }catch(Exception e){status.setText("Could not read music library: "+e.getMessage());}
        status.setText(tracks.size()+" offline tracks found"); showTab("SONGS");
    }
    private void showTab(String tab){
        content.removeAllViews();
        if(tab.equals("EQUALIZER")){showEqualizer();return;}
        if(tab.equals("ALBUMS")||tab.equals("ARTISTS")||tab.equals("FOLDERS")){
            LinkedHashSet<String> values=new LinkedHashSet<>();
            for(Track t:tracks)values.add(tab.equals("ALBUMS")?t.album:tab.equals("ARTISTS")?t.artist:new File(t.path).getParent());
            for(String s:values){TextView v=text("♫   "+(s==null?"Unknown":s),16,Color.WHITE);v.setPadding(dp(10),dp(15),dp(10),dp(15));content.addView(v);}
            return;
        }
        if(tab.equals("PLAYLISTS")){
            Button create=button("+ NEW PLAYLIST");content.addView(create);create.setOnClickListener(v->{
                EditText input=new EditText(this);input.setHint("Playlist name");
                new AlertDialog.Builder(this).setTitle("Create playlist").setView(input).setPositiveButton("Create",(d,w)->{
                    String n=input.getText().toString().trim();if(!n.isEmpty()){playlistNames.add(n);playlists.put(n,new ArrayList<Integer>());showTab("PLAYLISTS");}
                }).setNegativeButton("Cancel",null).show();});
            for(String n:playlistNames){TextView row=text("♫   "+n+"  ("+playlists.get(n).size()+")",16,Color.WHITE);row.setPadding(dp(10),dp(14),0,dp(14));content.addView(row);
                row.setOnClickListener(v->{content.removeAllViews();for(Integer i:playlists.get(n))addTrackRow(i);});}
            return;
        }
        for(int i=0;i<tracks.size();i++)addTrackRow(i);
    }
    private void addTrackRow(int i){
        Track t=tracks.get(i); LinearLayout row=new LinearLayout(this);row.setPadding(dp(8),dp(11),dp(4),dp(11));row.setGravity(Gravity.CENTER_VERTICAL);
        TextView info=text(t.name+"\n"+t.artist+"  •  "+t.album,14,Color.WHITE);row.addView(info,new LinearLayout.LayoutParams(0,-2,1));
        TextView add=text("＋",24,0xffd6a84f);row.addView(add);add.setOnClickListener(v->{
            if(playlistNames.isEmpty()){Toast.makeText(this,"Create a playlist first",Toast.LENGTH_SHORT).show();return;}
            String[] opts=playlistNames.toArray(new String[0]);new AlertDialog.Builder(this).setTitle("Add to playlist").setItems(opts,(d,w)->playlists.get(opts[w]).add(i)).show();
        });
        content.addView(row); View divider=new View(this);divider.setBackgroundColor(0xff292a30);content.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));
        row.setOnClickListener(v->play(i));
    }
    private void play(int i){
        if(i<0||i>=tracks.size())return;
        try{
            if(player!=null){player.release();player=null;}
            current=i;player=new MediaPlayer();player.setDataSource(this,tracks.get(i).uri());player.setOnPreparedListener(mp->{
                mp.start();playButton.setText("Ⅱ");title.setText(tracks.get(i).name);artist.setText(tracks.get(i).artist+"  •  "+tracks.get(i).album);
                seek.setMax(mp.getDuration());duration.setText(time(mp.getDuration()));attachEffects(mp.getAudioSessionId());
            });
            player.setOnCompletionListener(mp->next());player.prepareAsync();
        }catch(Exception e){Toast.makeText(this,"Can't play this track",Toast.LENGTH_SHORT).show();}
    }
    private void attachEffects(int session){
        try{if(equalizer!=null)equalizer.release();if(bassBoost!=null)bassBoost.release();if(virtualizer!=null)virtualizer.release();
            equalizer=new Equalizer(0,session);equalizer.setEnabled(true);
            bassBoost=new BassBoost(0,session);bassBoost.setEnabled(true);
            virtualizer=new Virtualizer(0,session);virtualizer.setEnabled(true);
        }catch(Exception ignored){}
    }
    private void showEqualizer(){
        TextView h=text("AUDIO EFFECTS",18,Color.WHITE);content.addView(h);
        if(equalizer==null){content.addView(text("Play a track to activate audio effects.",13,0xffaaaaaa));return;}
        TextView preset=text("Preset: "+equalizer.getPreset(),14,0xffd6a84f);content.addView(preset);
        for(short b=0;b<equalizer.getNumberOfBands();b++){
            final short band=b;TextView label=text("Band "+(b+1),13,Color.WHITE);content.addView(label);
            SeekBar bar=new SeekBar(this);bar.setMax(equalizer.getBandLevelRange()[1]-equalizer.getBandLevelRange()[0]);
            bar.setProgress(equalizer.getBandLevel(b)-equalizer.getBandLevelRange()[0]);content.addView(bar);
            bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}public void onProgressChanged(SeekBar s,int p,boolean u){if(u)equalizer.setBandLevel(band,(short)(p+equalizer.getBandLevelRange()[0]));}});
        }
        addEffectToggle("Bass boost",bassBoost);addEffectToggle("Virtualizer",virtualizer);
        Button presetBtn=button("NEXT EQUALIZER PRESET");content.addView(presetBtn);presetBtn.setOnClickListener(v->{short n=(short)((equalizer.getCurrentPreset()+1)%equalizer.getNumberOfPresets());equalizer.usePreset(n);showEqualizer();});
    }
    private void addEffectToggle(String name, android.media.audiofx.AudioEffect fx){
        if(fx==null)return;Switch sw=new Switch(this);sw.setText(name);sw.setTextColor(Color.WHITE);sw.setChecked(fx.getEnabled());content.addView(sw);sw.setOnCheckedChangeListener((b,c)->fx.setEnabled(c));
    }
    private void next(){if(!tracks.isEmpty())play((current+1)%tracks.size());}
    private void previous(){if(player!=null&&player.getCurrentPosition()>3000)player.seekTo(0);else if(!tracks.isEmpty())play((current-1+tracks.size())%tracks.size());}
    private String time(long ms){long s=ms/1000;return String.format(java.util.Locale.US,"%d:%02d",s/60,s%60);}
    @Override protected void onDestroy(){super.onDestroy();handler.removeCallbacksAndMessages(null);if(player!=null)player.release();if(equalizer!=null)equalizer.release();if(bassBoost!=null)bassBoost.release();if(virtualizer!=null)virtualizer.release();}
}
