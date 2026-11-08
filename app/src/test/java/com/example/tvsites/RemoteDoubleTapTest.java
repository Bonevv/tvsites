package com.example.tvsites;
import org.junit.Test;
import static org.junit.Assert.*;
public class RemoteDoubleTapTest {
    @Test public void twoReleasedTapsToggleButTripleTapDoesNotToggleTwice(){
        RemoteDoubleTap tap=new RemoteDoubleTap();
        assertFalse(tap.event(19,true,0,false,1000));assertFalse(tap.event(19,false,0,false,1050));
        assertTrue(tap.event(19,true,0,false,1200));assertFalse(tap.event(19,false,0,false,1250));
        assertFalse(tap.event(19,true,0,false,1400));
    }
    @Test public void aHeldKeyAndItsRepeatsAreNeverDoubleTaps(){
        RemoteDoubleTap tap=new RemoteDoubleTap();tap.event(20,true,0,false,1000);
        assertFalse(tap.event(20,true,1,false,1100));assertFalse(tap.event(20,true,2,false,1150));tap.event(20,false,0,false,1200);
        assertFalse(tap.event(20,true,0,false,1300));
    }
    @Test public void oppositeDirectionCancellationAndTimeoutResetGesture(){
        RemoteDoubleTap tap=new RemoteDoubleTap();tap.event(19,true,0,false,1000);tap.event(19,false,0,false,1050);
        assertFalse(tap.event(20,true,0,false,1100));tap.event(20,false,0,true,1150);assertFalse(tap.event(20,true,0,false,1200));
        tap.event(20,false,0,false,1250);assertFalse(tap.event(20,true,0,false,1700));tap.reset();assertFalse(tap.event(20,true,0,false,1750));
    }
}
