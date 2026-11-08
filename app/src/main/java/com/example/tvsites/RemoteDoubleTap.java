package com.example.tvsites;

/** Requires a release between two initial key downs; OS hold repeats never count as taps. */
final class RemoteDoubleTap {
    private int key=-1,held=-1;private long released=-1,pressed;private boolean repeated;
    boolean event(int code,boolean down,int repeat,boolean canceled,long now){
        if(canceled){reset();return false;}
        if(down){
            if(repeat>0){repeated=true;key=-1;return false;}
            if(held==code)return false;
            boolean twice=key==code && released>=0 && now-released<=350;
            held=code;pressed=now;repeated=twice;key=-1;released=-1;return twice;
        }
        if(held!=code)return false;held=-1;
        if(!repeated && now-pressed<=350){key=code;released=now;}else{key=-1;released=-1;}return false;
    }
    void reset(){key=held=-1;released=-1;repeated=false;}
}
